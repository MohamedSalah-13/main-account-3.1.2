package com.hamza.account.controller.items;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.events.StockCountPosted;
import com.hamza.account.features.inventory.ColumnKind;
import com.hamza.account.features.inventory.InventoryColumn;
import com.hamza.account.features.inventory.InventoryColumns;
import com.hamza.account.features.inventory.InventoryPage;
import com.hamza.account.features.inventory.InventoryQuery;
import com.hamza.account.features.inventory.InventoryRow;
import com.hamza.account.features.inventory.InventoryService;
import com.hamza.account.features.inventory.InventorySummary;
import com.hamza.account.features.inventory.StockFilter;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.MainGroupService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The inventory sheet: every item, what moved, what is left, and what it is worth.
 *
 * <h2>What the totals mean</h2>
 * The figures in the bottom bar are for <em>every</em> item the current search
 * matches, not for the rows on screen. They arrive from a {@code SELECT SUM(...)}
 * over the same filter that produced the rows - see {@code InventoryDao} - rather
 * than being added up from the loaded page, which is what they used to be. Turning a
 * page no longer changes them, and searching narrows them in step with the table.
 *
 * <h2>Loading</h2>
 * Every load runs on a background thread, because a DAO call here reaches the
 * database and blocking the JavaFX thread freezes the window. Results are stamped
 * with a token and a late one is dropped: with a debounced search box, a slow query
 * for "ا" can otherwise land after a fast one for "احمد" and put the wrong rows -
 * and the wrong totals - on screen.
 * <p>
 * The page is driven from {@code currentPageIndexProperty} alone. The page factory
 * only hands back the table now; it used to load as well, and with a second listener
 * doing the same thing every page turn ran the query twice.
 */
@Log4j2
@FxmlPath(pathFile = "items/inventory-view.fxml")
public class InventoryController {

    private final TableView<InventoryRow> tableView = new TableView<>();

    private final InventoryService inventoryService = ServiceRegistry.get(InventoryService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final MainGroupService mainGroupService = ServiceRegistry.get(MainGroupService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ExcelExportService excelExportService = new ExcelExportService();

    /**
     * Closed when the screen leaves the scene graph. The bus outlives every screen -
     * it is registered once for the process - so a listener left on it keeps this
     * controller, its table and its scene graph alive, and re-runs its reload once per
     * past opening of the screen.
     */
    private final Subscriptions subscriptions = new Subscriptions();

    /** What is currently on screen. Every reload is derived from it. */
    private InventoryQuery query = InventoryQuery.all();

    /**
     * Identifies the newest load. A task whose token is no longer the latest has been
     * overtaken and its result is discarded.
     */
    private long loadToken;

    /** Set while the pagination is being resized in code, so it does not reload. */
    private boolean adjustingPagination;

    /**
     * Set while the filter controls are being put back in step with {@link #query} in
     * code, so their listeners do not read the change as the user making it and start
     * a second load.
     */
    private boolean adjustingFilters;

    @FXML
    private TextField textSearch;
    @FXML
    private Text textSumPurchase, textSumSales, textItemCount, textLowStock;
    @FXML
    private Label labelTotalQuantity, labelExpectedProfit, labelStockStates, labelCostHint, labelStatus;
    @FXML
    private Button btnPrint, btnRefresh, btnExcel, btnClearFilters;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Pagination pagination;
    @FXML
    private ChoiceBox<StockFilter> choiceLevel;
    @FXML
    private ComboBox<Stock> comboStock;
    @FXML
    private ChoiceBox<GroupChoice> choiceGroup;
    @FXML
    private CheckBox checkInactive;
    @FXML
    private VBox tileLowStock;
    @FXML
    private AnchorPane root;

    @FXML
    public void initialize() {
        buildTable();
        buildPagination();
        buildLevelPicker();
        buildStockPicker();
        buildGroupPicker();
        buildActions();
        explainTotals();
        subscribeToChanges();
        // The screen used to open showing 0.00 and stay there until the user changed
        // a page or typed something: nothing loaded the totals on the way in.
        load(query);
    }

    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------

    /**
     * The table is built from {@link InventoryColumns#ALL}. Everything below is the
     * one piece of code that turns a declaration into a {@code TableColumn}; a new
     * column is a line in that catalogue and nothing here.
     * <p>
     * What this replaced was eight near-identical blocks followed by
     * {@code getColumns().removeAll(List.of(5, 4, 3, 1))} - columns deleted by
     * position, so adding an annotated field to the item model removed different
     * ones. The columns also had no ids, so {@link TableSetting} remembered the
     * user's widths against an index that moved with them.
     */
    private void buildTable() {
        tableView.setId("inventoryTable");
        tableView.getStyleClass().add("modern-table");
        tableView.setPlaceholder(new Label(LanguageManager.getInstance().getString("item.inventory.placeholder.initial")));

        InventoryColumns.ALL.forEach(column -> tableView.getColumns().add(build(column)));
        tableView.setRowFactory(view -> new StockRow());

        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    /**
     * A row that carries its stock state as a style class, so the shading lives in the
     * theme and works in both light and dark rather than being an inline colour that
     * only suits one of them.
     * <p>
     * The state comes from {@link InventoryRow#level()}, which is
     * {@code StockLevel.of} - the same function the sales screens use - so a shaded row
     * here and a warning raised there can never mean different things.
     */
    private static final class StockRow extends TableRow<InventoryRow> {

        private static final List<String> STATES =
                List.of("stock-low", "stock-out", "stock-negative", "stock-inactive");

        @Override
        protected void updateItem(InventoryRow row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().removeAll(STATES);
            if (empty || row == null) {
                return;
            }
            switch (row.level()) {
                case AT_MINIMUM -> getStyleClass().add("stock-low");
                case OUT_OF_STOCK -> getStyleClass().add("stock-out");
                case NEGATIVE -> getStyleClass().add("stock-negative");
                case OK -> {
                }
            }
            if (!row.active()) {
                getStyleClass().add("stock-inactive");
            }
        }
    }

    private TableColumn<InventoryRow, ?> build(InventoryColumn column) {
        if (column.isGroup()) {
            TableColumn<InventoryRow, Object> group = new TableColumn<>(column.title());
            group.setId(column.id());
            column.children().forEach(child -> group.getColumns().add(build(child)));
            return group;
        }
        return column.kind() == ColumnKind.TEXT ? text(column) : number(column);
    }

    private TableColumn<InventoryRow, String> text(InventoryColumn column) {
        TableColumn<InventoryRow, String> tableColumn = new TableColumn<>(column.title());
        tableColumn.setId(column.id());
        tableColumn.setPrefWidth(column.prefWidth());
        tableColumn.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(column.textOf(f.getValue())));
        return tableColumn;
    }

    /**
     * A numeric column. The cell value stays a {@code Double} so sorting compares
     * numbers rather than the formatted text - "1,000" sorts before "9" as a string -
     * and only the display goes through {@link ColumnKind#format}.
     */
    private TableColumn<InventoryRow, Double> number(InventoryColumn column) {
        TableColumn<InventoryRow, Double> tableColumn = new TableColumn<>(column.title());
        tableColumn.setId(column.id());
        tableColumn.setPrefWidth(column.prefWidth());
        tableColumn.setCellValueFactory(f -> new ReadOnlyObjectWrapper<>(column.numberOf(f.getValue())));
        tableColumn.setCellFactory(c -> {
            TableCell<InventoryRow, Double> cell = new TableCell<>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : column.kind().format(item));
                }
            };
            cell.setStyle("-fx-alignment: CENTER-RIGHT;");
            return cell;
        });
        return tableColumn;
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void buildPagination() {
        // Layout only. Loading is driven by the index listener below, so the two
        // cannot both fire for one page turn.
        pagination.setPageFactory(pageIndex -> tableView);
        pagination.setPageCount(1);
        pagination.currentPageIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (adjustingPagination) {
                return;
            }
            load(query.withPage(newValue.intValue()));
        });
        VBox.setVgrow(pagination, Priority.ALWAYS);
    }

    private void buildStockPicker() {
        try {
            comboStock.setItems(FXCollections.observableArrayList(stockService.getStocks()));
            comboStock.setConverter(new StringConverter<>() {
                @Override public String toString(Stock stock) { return stock == null ? "" : stock.getName(); }
                @Override public Stock fromString(String value) { return null; }
            });
            comboStock.getSelectionModel().select(comboStock.getItems().stream()
                    .filter(stock -> stock.getId() == DefaultStock.ID).findFirst().orElse(null));
            comboStock.valueProperty().addListener((observable, oldStock, newStock) -> {
                if (!adjustingFilters && newStock != null && newStock.getId() != query.stockId()) {
                    load(query.withStock(newStock.getId()));
                }
            });
        } catch (DaoException e) {
            log.error("Failed to read stocks for inventory", e);
        }
    }

    /**
     * The stock-state filter. It narrows the query rather than the loaded rows, so the
     * totals and the row count narrow with it - filtering in memory would have left
     * the header describing every item while the table showed a handful.
     */
    private void buildLevelPicker() {
        choiceLevel.getItems().setAll(StockFilter.values());
        choiceLevel.setConverter(new StringConverter<>() {
            @Override
            public String toString(StockFilter filter) {
                return filter == null ? "" : filter.title();
            }

            @Override
            public StockFilter fromString(String string) {
                return StockFilter.ALL;
            }
        });
        choiceLevel.setValue(StockFilter.ALL);
        choiceLevel.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!adjustingFilters && newValue != null) {
                load(query.withLevel(newValue));
            }
        });
    }

    /**
     * The group filter, read once when the screen opens.
     * <p>
     * It offers main groups rather than sub groups: a shop has a handful of the former
     * and often hundreds of the latter, and a list of hundreds is not a filter anyone
     * uses. {@code items} carries the sub group, so the query reaches the main one
     * through {@code sub_group.main_id} - see {@code InventoryDao.filter}.
     * <p>
     * Failing to read the groups leaves the picker holding "الكل" and the screen fully
     * usable; it is a filter, not the report.
     */
    private void buildGroupPicker() {
        GroupChoice all = new GroupChoice(InventoryQuery.ALL_GROUPS,
                LanguageManager.getInstance().getString("item.inventory.group.all"));
        choiceGroup.setConverter(new StringConverter<>() {
            @Override
            public String toString(GroupChoice choice) {
                return choice == null ? "" : choice.name();
            }

            @Override
            public GroupChoice fromString(String string) {
                return all;
            }
        });
        choiceGroup.getItems().add(all);
        choiceGroup.setValue(all);
        choiceGroup.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!adjustingFilters && newValue != null) {
                load(query.withMainGroup(newValue.id()));
            }
        });

        Task<List<GroupChoice>> task = new Task<>() {
            @Override
            protected List<GroupChoice> call() throws DaoException {
                return mainGroupService.getMainGroupList().stream()
                        .map(group -> new GroupChoice(group.getId(), group.getName()))
                        .toList();
            }
        };
        task.setOnSucceeded(event -> choiceGroup.getItems().addAll(task.getValue()));
        task.setOnFailed(event ->
                log.error("Failed to read the item groups for the inventory filter", task.getException()));

        Thread thread = new Thread(task, "inventory-groups");
        thread.setDaemon(true);
        thread.start();
    }

    /** An entry in the group picker; the "no group" option is built fresh in {@link #buildGroupPicker()}. */
    private record GroupChoice(int id, String name) {
    }

    /**
     * Reloads when something happens that changes what is in stock.
     * <p>
     * The screen had no subscription at all: an invoice could be saved in another tab
     * and this sheet would go on showing balances from before it. A purchase return
     * carries {@code PURCHASE} and a sales return {@code SALES}, so both sides are
     * taken - every one of them moves stock, which is the only thing this screen
     * reports on.
     */
    private void subscribeToChanges() {
        subscriptions.add(eventBus.subscribe(InvoiceSaved.class, event -> reload()));
        subscriptions.add(eventBus.subscribe(ItemSaved.class, event -> reload()));
        subscriptions.add(eventBus.subscribe(ItemsChanged.class, event -> reload()));
        // A posted count corrects balances without anything being bought or sold, and
        // the sheet shows the correction in its own column.
        subscriptions.add(eventBus.subscribe(StockCountPosted.class, event -> reload()));
        subscriptions.disposeWith(root);
    }

    /**
     * Reloads the current view. Separate from {@link #load} so a listener cannot be
     * mistaken for something that changes the filter.
     */
    private void reload() {
        load(query);
    }

    private void buildActions() {
        // The refresh button had no handler at all: it was declared in the FXML,
        // injected here, and never wired to anything.
        btnRefresh.setOnAction(actionEvent -> reload());
        btnPrint.setOnAction(actionEvent -> printSheet());
        btnExcel.setOnAction(actionEvent -> exportToExcel());
        btnClearFilters.setOnAction(actionEvent -> load(InventoryQuery.all().withStock(query.stockId())));

        checkInactive.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!adjustingFilters) {
                load(query.withInactive(newValue));
            }
        });

        // The tile is the only place the low-stock count appears, so it is also the
        // obvious place to ask for those items; clicking it narrows the table to them
        // and clicking again goes back.
        tileLowStock.setOnMouseClicked(event -> load(query.withLevel(
                query.level() == StockFilter.LOW ? StockFilter.ALL : StockFilter.LOW)));

        PauseTransition pause = new PauseTransition(Duration.millis(400));
        textSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (adjustingFilters) {
                return;
            }
            pause.setOnFinished(event -> load(query.withSearch(newValue)));
            pause.playFromStart();
        });

        // A report screen is read, so the keys that matter are the ones that get you
        // back to the search box and the ones that redraw it.
        root.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                reload();
            } else if (event.getCode() == KeyCode.F && event.isControlDown()) {
                textSearch.requestFocus();
                textSearch.selectAll();
            }
        });
        Platform.runLater(textSearch::requestFocus);
    }

    private void explainTotals() {
        Tooltip tooltip = new Tooltip(LanguageManager.getInstance().getString("item.inventory.tooltip.totals.explain"));
        Tooltip.install(textSumPurchase, tooltip);
        Tooltip.install(textSumSales, tooltip);
        Tooltip.install(textItemCount, tooltip);
        Tooltip.install(tileLowStock, new Tooltip(LanguageManager.getInstance().getString("item.inventory.tooltip.low.stock.tile")));
    }

    /**
     * Puts the filter controls back in step with {@link #query}. Needed because the
     * query can change without the user touching a control - "مسح الفلاتر" resets all
     * of them at once, and the low-stock tile sets the stock state.
     */
    private void syncFilters() {
        adjustingFilters = true;
        try {
            if (!textSearch.getText().equals(query.search())) {
                textSearch.setText(query.search());
            }
            choiceLevel.setValue(query.level());
            comboStock.getItems().stream().filter(stock -> stock.getId() == query.stockId())
                    .findFirst().ifPresent(comboStock.getSelectionModel()::select);
            checkInactive.setSelected(query.includeInactive());
            choiceGroup.getItems().stream()
                    .filter(choice -> choice.id() == query.mainGroupId())
                    .findFirst()
                    .ifPresent(choiceGroup::setValue);
            btnClearFilters.setDisable(!query.isNarrowed());
        } finally {
            adjustingFilters = false;
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads {@code next} in the background and shows it if nothing newer has been
     * asked for by the time it arrives.
     */
    private void load(InventoryQuery next) {
        query = next;
        long token = ++loadToken;
        progress.setVisible(true);

        Task<InventoryPage> task = new Task<>() {
            @Override
            protected InventoryPage call() throws DaoException {
                return inventoryService.load(next);
            }
        };
        task.setOnSucceeded(event -> {
            if (token != loadToken) {
                return;
            }
            progress.setVisible(false);
            show(task.getValue());
        });
        task.setOnFailed(event -> {
            if (token != loadToken) {
                return;
            }
            progress.setVisible(false);
            Throwable error = task.getException();
            // A failure here used to be wrapped in a RuntimeException thrown out of
            // the page factory: no Arabic message, and a broken Pagination.
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.inventory.error.load.title"), error);
        });

        Thread thread = new Thread(task, "inventory-load-" + token);
        thread.setDaemon(true);
        thread.start();
    }

    private void show(InventoryPage page) {
        int pageCount = page.pageCount(query.pageSize());

        // The filter can shrink under a page that no longer exists - search while
        // sitting on page 7 and the two matches are all on page 1. Land on the last
        // real page instead of showing an empty table that reads as "no matches".
        int clamped = Math.min(query.page(), pageCount - 1);
        if (clamped != query.page()) {
            load(query.withPage(clamped));
            return;
        }

        tableView.setItems(FXCollections.observableArrayList(page.rows()));

        adjustingPagination = true;
        try {
            pagination.setPageCount(pageCount);
            pagination.setCurrentPageIndex(query.page());
        } finally {
            adjustingPagination = false;
        }

        syncFilters();
        showSummary(page.summary());
        showStatus(page);
        var lm = LanguageManager.getInstance();
        tableView.setPlaceholder(new Label(query.isNarrowed()
                ? lm.getString("item.inventory.placeholder.no.match")
                : lm.getString("item.inventory.placeholder.empty")));
    }

    /**
     * Which slice of the whole is on screen. Without it the pagination says "page 3"
     * and nothing says of how many rows, which is exactly the question a stocktake asks.
     */
    private void showStatus(InventoryPage page) {
        long total = page.totalRows();
        if (total == 0) {
            labelStatus.setText("");
            return;
        }
        long first = (long) query.page() * query.pageSize() + 1;
        long last = first + page.rows().size() - 1;
        labelStatus.setText(LanguageManager.getInstance().getString("item.inventory.label.status",
                count(first), count(last), count(total)));
    }

    /**
     * Written through the same {@link ColumnKind} the matching columns use, so the
     * total under a column is formatted exactly like the figures above it.
     */
    private void showSummary(InventorySummary summary) {
        var lm = LanguageManager.getInstance();
        textSumPurchase.setText(ColumnKind.MONEY.format(summary.valueAtCost()));
        textSumSales.setText(ColumnKind.MONEY.format(summary.valueAtSale()));
        textItemCount.setText(count(summary.itemCount()));
        textLowStock.setText(count(summary.lowStockCount()));

        labelCostHint.setText(query.isNarrowed()
                ? lm.getString("item.inventory.hint.cost.narrowed")
                : lm.getString("item.inventory.hint.cost.all"));
        // The gap between the two valuations, which is the margin still sitting on the
        // shelf. Not a profit until it is sold, so it is a hint and not a tile.
        labelExpectedProfit.setText(lm.getString("item.inventory.hint.expected.profit",
                ColumnKind.MONEY.format(summary.valueAtSale() - summary.valueAtCost())));
        labelTotalQuantity.setText(lm.getString("item.inventory.hint.total.quantity",
                ColumnKind.QUANTITY.format(summary.totalQuantity())));
        labelStockStates.setText(lm.getString("item.inventory.hint.stock.states",
                count(summary.outOfStockCount()), count(summary.negativeCount())));
    }

    private static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    // ------------------------------------------------------------------
    // Printing and export
    // ------------------------------------------------------------------

    /**
     * Writes the whole filtered sheet to a spreadsheet.
     * <p>
     * The file is asked for first and the rows only fetched once the user has chosen
     * one, so cancelling costs nothing. As with printing, the rows are re-read unpaged:
     * exporting the table's items would export the fifty rows on screen.
     */
    private void exportToExcel() {
        InventoryQuery snapshot = query;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(LanguageManager.getInstance().getString("item.inventory.dialog.export.title"));
        chooser.setInitialFileName("inventory-" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }

        btnExcel.setDisable(true);
        progress.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws DaoException, IOException {
                // The totals are re-read rather than taken off the screen so the file's
                // totals row is the database's answer, not whatever the header happened
                // to be showing when the button was pressed.
                excelExportService.exportInventoryToExcel(
                        inventoryService.loadAll(snapshot),
                        InventoryColumns.ALL,
                        inventoryService.summary(snapshot),
                        file.getAbsolutePath());
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            btnExcel.setDisable(false);
            progress.setVisible(false);
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance()
                    .getString("item.inventory.msg.export.success", file.getName()));
        });
        task.setOnFailed(event -> {
            btnExcel.setDisable(false);
            progress.setVisible(false);
            Throwable error = task.getException();
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.inventory.error.export.title"), error);
        });

        Thread thread = new Thread(task, "inventory-export");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Prints everything the current search matches. The rows are fetched again
     * unpaged, because the table only ever holds one page - printing used to hand the
     * report {@code tableView.getItems()} and so printed fifty items out of however
     * many the shop has.
     */
    private void printSheet() {
        InventoryQuery snapshot = query;
        btnPrint.setDisable(true);
        progress.setVisible(true);

        Task<PrintData> task = new Task<>() {
            @Override
            protected PrintData call() throws DaoException {
                return new PrintData(inventoryService.loadAll(snapshot), stockName());
            }
        };
        task.setOnSucceeded(event -> {
            btnPrint.setDisable(false);
            progress.setVisible(false);
            PrintData data = task.getValue();
            new Print_Reports().printInventoryByTable(data.rows(), data.stockName());
        });
        task.setOnFailed(event -> {
            btnPrint.setDisable(false);
            progress.setVisible(false);
            Throwable error = task.getException();
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.inventory.error.print.title"), error);
        });

        Thread thread = new Thread(task, "inventory-print");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Header for the printed inventory sheet. This used to be whichever warehouse
     * the user had picked; with a single stock it is simply that stock's name, read
     * from the database so a renamed stock still prints correctly.
     */
    private String stockName() {
        try {
            Stock stock = comboStock.getValue();
            return stock == null ? stockService.getDefaultStock().getName() : stock.getName();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return "";
        }
    }

    private record PrintData(List<InventoryRow> rows, String stockName) {
    }
}
