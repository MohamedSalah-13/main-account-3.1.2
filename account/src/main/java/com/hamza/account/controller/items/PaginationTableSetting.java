package com.hamza.account.controller.items;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The paging, filtering and reloading behind the items table.
 * <p>
 * Four rules shape it, and each one was a defect before it was a rule:
 * <ul>
 *   <li><b>Nothing touches the database on the JavaFX thread.</b> Every read runs on one
 *       background thread and only the assignment to the table comes back to the FX
 *       thread. A slow query makes the screen late, never frozen.</li>
 *   <li><b>A superseded read is dropped, not displayed.</b> Reads are stamped with a
 *       token; a result whose token is no longer the current one is discarded, so a slow
 *       search for "ا" cannot land on top of a fast one for "احمد". Same idea as
 *       {@code ItemSuggestionField}.</li>
 *   <li><b>Reloading keeps the operator's place.</b> Page index, filter and the selected
 *       row survive a reload - {@link #reload()} is what a save or a delete asks for.
 *       Only a change to <em>which rows there are</em> - a new search, a new filter -
 *       goes back to the first page, through {@link #refresh()}.</li>
 *   <li><b>One filter object drives the page and the count.</b> Every way of narrowing
 *       the list - the search box, the group tree, a quick chip, the filter panel - is a
 *       new {@link ItemCatalogFilter}, and both reads are built from it. A screen that
 *       filtered the page one way and counted another would page over rows that are not
 *       there.</li>
 * </ul>
 */
@Log4j2
@RequiredArgsConstructor
public class PaginationTableSetting {

    private static final int ROWS_PER_PAGE = 50;
    /**
     * Long enough that typing a word is one query rather than one per letter, short
     * enough not to feel like a pause. The reads it guards are off the FX thread, so
     * this is about query load, not about responsiveness.
     */
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(300);

    private final TableView<ItemsModel> tableView;
    private final ItemsService itemsService;
    private final TextField txtSearch;
    private final Pagination pagination;

    /** Everything narrowing the list right now. Replaced wholesale, never mutated. */
    private ItemCatalogFilter filter = ItemCatalogFilter.EMPTY;
    private boolean initialized;

    /**
     * How many rows the current filter matches in the database - not how many are on this
     * page. The status bar reads it, which is the difference between a screen that says
     * "50" for every filter and one that tells the operator what they actually asked for.
     */
    private final ReadOnlyIntegerWrapper totalRows = new ReadOnlyIntegerWrapper(0);

    /**
     * Stamps every read. Incremented whenever what should be on screen changes, so any
     * result already in flight is recognised as stale when it arrives.
     */
    private final AtomicLong token = new AtomicLong();
    /** What the table currently holds, so the page factory does not re-query for it. */
    private String loadedSignature;
    /**
     * One daemon thread for every instance of this screen, deliberately.
     * <p>
     * The tab can be opened and closed repeatedly and nothing here is told when it
     * closes, so a per-instance executor would leak a thread each time. Serialising the
     * catalog reads is no loss - a second read of the same table only ever supersedes
     * the first.
     */
    private static final ExecutorService READER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "items-catalog-reader");
                thread.setDaemon(true);
                return thread;
            });

    public ReadOnlyIntegerProperty totalRowsProperty() {
        return totalRows.getReadOnlyProperty();
    }

    public int rowsPerPage() {
        return ROWS_PER_PAGE;
    }

    public ItemCatalogFilter filter() {
        return filter;
    }

    public void initializePagination() {
        if (!initialized) {
            initialized = true;
            pagination.setPageFactory(pageIndex -> {
                load(pageIndex);
                return tableView;
            });
            PauseTransition pause = new PauseTransition(SEARCH_DEBOUNCE);
            pause.setOnFinished(event -> {
                String typed = txtSearch.getText() == null ? "" : txtSearch.getText().trim();
                if (typed.equals(filter.searchText())) return;
                setFilter(filter.withSearch(typed));
            });
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> pause.playFromStart());
        }
        reload();
    }

    /**
     * Narrows the list, from the first page.
     * <p>
     * Every affordance on the screen comes through here - the search box, the group tree,
     * a chip, the panel - because a filter that reached the query by any other route would
     * be a filter the count does not know about.
     */
    public void setFilter(ItemCatalogFilter newFilter) {
        ItemCatalogFilter next = newFilter == null ? ItemCatalogFilter.EMPTY : newFilter;
        // An identical filter is not a change. The group tree selects its root as it
        // finishes loading, which arrives here as "no group" - already true - and without
        // this guard that would cost a query and send the operator back to the first page
        // every time the tree was rebuilt.
        if (next.equals(this.filter)) return;
        this.filter = next;
        refresh();
    }

    public void setGroupFilter(Integer mainGroupId, Integer subGroupId) {
        setFilter(filter.withGroup(mainGroupId, subGroupId));
    }

    /**
     * Re-reads what is on screen now, keeping the page, the filter and the selected row.
     * <p>
     * This is what a delete or a wholesale change asks for. It is not what a single
     * saved item asks for - the screen replaces that row in place and never comes here,
     * which is why editing the fifth row no longer sends the operator back to the first
     * page to find the sixth.
     */
    public void reload() {
        token.incrementAndGet();
        loadedSignature = null;
        countThenPage(pagination.getCurrentPageIndex());
    }

    /** Re-reads from the first page: the set of rows itself has changed. */
    public void refresh() {
        token.incrementAndGet();
        loadedSignature = null;
        countThenPage(0);
    }

    /**
     * Re-reads one item and puts it back where it was, leaving the page, the filter, the
     * scroll position and the selection exactly as they were.
     * <p>
     * This is the whole answer to editing the fifth row and being sent back to the first
     * page: a saved item changes one row, so only that row is read again. An item that is
     * not on screen - a newly created one, or one edited from somewhere else - cannot be
     * put back in place, and falls through to {@link #refresh()}.
     */
    public void refreshRow(int itemId) {
        int index = indexOf(itemId);
        if (index < 0) {
            refresh();
            return;
        }
        long stamp = token.get();
        READER.execute(() -> {
            try {
                ItemsModel row = itemsService.getCatalogItem(itemId);
                if (row == null) return;
                Platform.runLater(() -> {
                    if (stamp != token.get()) return;
                    int current = indexOf(itemId);
                    if (current < 0) return;
                    // Read where the selection is now, not where it was when the read was
                    // asked for: the in-table edit moves to the next row as soon as it
                    // saves, and this lands afterwards. Restoring a selection captured
                    // before the save would drag the operator back a row every time.
                    boolean wasSelected = tableView.getSelectionModel().getSelectedIndex() == current;
                    tableView.getItems().set(current, row);
                    if (wasSelected) tableView.getSelectionModel().select(current);
                });
            } catch (DaoException e) {
                log.error("Failed to re-read the saved item {}", itemId, e);
            }
        });
    }

    private int indexOf(int itemId) {
        List<ItemsModel> rows = tableView.getItems();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() == itemId) return index;
        }
        return -1;
    }

    private void countThenPage(int preferredPageIndex) {
        long stamp = token.get();
        ItemCatalogFilter asked = filter;
        READER.execute(() -> {
            try {
                int totalItems = itemsService.getCatalogCount(asked);
                int pageCount = Math.max(1, (totalItems + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
                int pageIndex = Math.min(Math.max(preferredPageIndex, 0), pageCount - 1);
                Platform.runLater(() -> {
                    if (isStale(stamp, asked)) return;
                    totalRows.set(totalItems);
                    pagination.setPageCount(pageCount);
                    pagination.setCurrentPageIndex(pageIndex);
                    load(pageIndex);
                });
            } catch (DaoException e) {
                log.error("Failed to count the items for the catalog", e);
            }
        });
    }

    /**
     * Reads one page in the background and hands it to the table - a page of the whole
     * catalog or a page of a filtered list, which are now the same thing.
     * <p>
     * Called both by the pagination control and directly after a count, which would read
     * the same page twice; the signature is what makes the second call a no-op. It is
     * cleared by {@link #reload()} and {@link #refresh()}, so asking for the same page
     * again after a save still re-reads it.
     */
    private void load(int pageIndex) {
        long stamp = token.get();
        ItemCatalogFilter asked = filter;
        String signature = stamp + "|" + asked + "|" + pageIndex;
        if (signature.equals(loadedSignature)) return;
        loadedSignature = signature;

        Integer selectedId = selectedItemId();
        READER.execute(() -> {
            try {
                List<ItemsModel> rows = itemsService.getCatalogProducts(
                        asked, ROWS_PER_PAGE, pageIndex * ROWS_PER_PAGE);
                Platform.runLater(() -> {
                    if (isStale(stamp, asked)) return;
                    tableView.setItems(FXCollections.observableArrayList(rows));
                    restoreSelection(selectedId);
                });
            } catch (DaoException e) {
                Platform.runLater(() -> loadedSignature = null);
                log.error("Failed to load a page of the items table", e);
            }
        });
    }

    /**
     * True once the answer being carried is for a question nobody is asking any more -
     * an older filter, or anything at all if the screen has since been told to reload.
     */
    private boolean isStale(long stamp, ItemCatalogFilter asked) {
        return stamp != token.get() || !asked.equals(filter);
    }

    private Integer selectedItemId() {
        ItemsModel selected = tableView.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    /**
     * Puts the selection back on the row the operator had, by id: the row objects are
     * rebuilt by every read, so the previously selected instance is never in the new
     * list. A row that is no longer on this page simply leaves the selection empty.
     */
    private void restoreSelection(Integer selectedId) {
        if (selectedId == null) return;
        List<ItemsModel> rows = tableView.getItems();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() == selectedId) {
                tableView.getSelectionModel().select(index);
                tableView.scrollTo(index);
                return;
            }
        }
    }
}
