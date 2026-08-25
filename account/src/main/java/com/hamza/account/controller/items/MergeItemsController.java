package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.inventory.ColumnKind;
import com.hamza.account.features.itemmerge.ItemMergeCandidate;
import com.hamza.account.features.itemmerge.ItemMergePreview;
import com.hamza.account.features.itemmerge.ItemMergeResult;
import com.hamza.account.features.itemmerge.ItemMergeService;
import com.hamza.account.features.itemmerge.MergeGroupBy;
import com.hamza.account.model.domain.CardItems;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CardItemService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The merge screen (دمج الأصناف).
 *
 * <h2>What it is for</h2>
 * A shop that entered one item per barcode - which is what the application required
 * before {@code item_barcodes} arrived - ends up with five rows for five flavours of the
 * same packet, each with its own years of invoices. This screen puts those rows next to
 * each other, shows what each one has actually been used for, and folds the duplicates
 * into the survivor.
 *
 * <h2>What it decides and what it does not</h2>
 * Nothing here decides that two items are the same thing in the world; the grouping only
 * proposes. The user picks the target, picks the sources, reads what would move, and
 * commits. Everything after that button is {@link ItemMergeService}'s - one transaction,
 * all of it or none of it - and this class does no arithmetic on the way.
 *
 * <h2>Choosing the target</h2>
 * The two columns that settle it are the number of operations and the last movement: of
 * a group of near-identical rows, the one still being sold is the survivor and the ones
 * that stopped moving are the duplicates. The table is sorted that way to begin with.
 */
@Log4j2
@FxmlPath(pathFile = "items/merge-items-view.fxml")
public class MergeItemsController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Enough to work through a catalogue's duplicates; a "same price" grouping could otherwise return most of it. */
    private static final int LIMIT = 500;

    private final ItemMergeService mergeService = ServiceRegistry.get(ItemMergeService.class);
    private final CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final ObservableList<ItemMergeCandidate> candidates = FXCollections.observableArrayList();
    private final ObservableList<CardItems> operations = FXCollections.observableArrayList();

    /** The survivor. Null until the user says so - there is no sensible default. */
    private ItemMergeCandidate target;

    @FXML
    private TableView<ItemMergeCandidate> tableCandidates;
    @FXML
    private TableView<CardItems> tableOperations;
    @FXML
    private ComboBox<MergeGroupBy> comboGroupBy;
    @FXML
    private Button btnRefresh, btnSetTarget, btnPreview, btnMerge;
    @FXML
    private Label labelTarget, labelPreview;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private AnchorPane root;

    @FXML
    public void initialize() {
        buildGroupBy();
        buildCandidatesTable();
        buildOperationsTable();
        buildActions();
        loadCandidates();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void buildGroupBy() {
        comboGroupBy.setItems(FXCollections.observableArrayList(MergeGroupBy.values()));
        comboGroupBy.setConverter(new StringConverter<>() {
            @Override
            public String toString(MergeGroupBy groupBy) {
                return groupBy == null ? "" : LanguageManager.getInstance().getString(groupBy.titleKey());
            }

            @Override
            public MergeGroupBy fromString(String text) {
                return null;
            }
        });
        comboGroupBy.getSelectionModel().select(MergeGroupBy.NAME);
    }

    private void buildCandidatesTable() {
        var lm = LanguageManager.getInstance();
        tableCandidates.setId("mergeCandidatesTable");
        tableCandidates.setItems(candidates);
        tableCandidates.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableCandidates.setPlaceholder(new Label(lm.getString("item.merge.placeholder")));

        tableCandidates.getColumns().add(role());
        tableCandidates.getColumns().add(text("name", lm.getString("item.merge.column.item"),
                ItemMergeCandidate::name, 240));
        tableCandidates.getColumns().add(text("barcode", lm.getString("column.barcode"),
                ItemMergeCandidate::barcode, 130));
        tableCandidates.getColumns().add(text("unit", lm.getString("item.column.unit"),
                ItemMergeCandidate::unitName, 90));
        tableCandidates.getColumns().add(text("price", lm.getString("item.merge.column.price"),
                candidate -> ColumnKind.MONEY.format(candidate.sellPrice().doubleValue()), 100));
        tableCandidates.getColumns().add(text("lines", lm.getString("item.merge.column.lines"),
                candidate -> String.valueOf(candidate.lineCount()), 100));
        tableCandidates.getColumns().add(text("lastMovement", lm.getString("item.merge.column.last.movement"),
                candidate -> candidate.lastMovement() == null ? "" : candidate.lastMovement().format(DAY), 120));
        tableCandidates.getColumns().add(text("group", lm.getString("item.merge.column.group"),
                ItemMergeCandidate::groupKey, 160));

        // Selecting a row shows what was done with that item, which is the question the
        // screen exists to answer - so it is one click, not a second screen.
        tableCandidates.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> loadOperations(selected));
    }

    /**
     * Says which row is the target and which of the rest could still be merged into it.
     * <p>
     * A row of a different base unit, or one tracking expiry the target does not, would
     * be refused by the service; saying so here means the user finds out while reading
     * the table rather than after pressing the button.
     */
    private TableColumn<ItemMergeCandidate, String> role() {
        var lm = LanguageManager.getInstance();
        TableColumn<ItemMergeCandidate, String> column = new TableColumn<>(lm.getString("item.merge.column.role"));
        column.setId("role");
        column.setPrefWidth(90);
        column.setCellValueFactory(feature -> {
            ItemMergeCandidate candidate = feature.getValue();
            if (target == null) {
                return new ReadOnlyObjectWrapper<>("");
            }
            if (candidate.id() == target.id()) {
                return new ReadOnlyObjectWrapper<>(lm.getString("item.merge.role.target"));
            }
            return new ReadOnlyObjectWrapper<>(candidate.canMergeInto(target)
                    ? lm.getString("item.merge.role.source")
                    : lm.getString("item.merge.role.blocked"));
        });
        return column;
    }

    private void buildOperationsTable() {
        var lm = LanguageManager.getInstance();
        tableOperations.setId("mergeOperationsTable");
        tableOperations.setItems(operations);
        tableOperations.setPlaceholder(new Label(lm.getString("item.merge.operations.placeholder")));

        tableOperations.getColumns().add(cardText("date", lm.getString("column.date"),
                row -> row.getInvoice_date() == null ? "" : row.getInvoice_date().format(DAY), 110));
        tableOperations.getColumns().add(cardText("type", lm.getString("item.merge.column.operation"),
                CardItems::getProcessTypeName, 120));
        tableOperations.getColumns().add(cardText("invoice", lm.getString("invoice.number"),
                row -> String.valueOf(row.getInvoice_num()), 100));
        tableOperations.getColumns().add(cardText("party", lm.getString("item.merge.column.party"),
                CardItems::getName_account, 180));
        tableOperations.getColumns().add(cardText("unit", lm.getString("item.column.unit"),
                CardItems::getType_name, 90));
        tableOperations.getColumns().add(cardText("quantity", lm.getString("column.quantity"),
                row -> ColumnKind.QUANTITY.format(row.getQuantity()), 100));
        tableOperations.getColumns().add(cardText("price", lm.getString("item.merge.column.price"),
                row -> ColumnKind.MONEY.format(row.getPrice()), 100));

        TableSetting.tableMenuSetting(getClass(), tableOperations);
    }

    private TableColumn<ItemMergeCandidate, String> text(String id, String title,
                                                         Function<ItemMergeCandidate, String> value, double width) {
        TableColumn<ItemMergeCandidate, String> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(feature -> new ReadOnlyObjectWrapper<>(value.apply(feature.getValue())));
        return column;
    }

    private TableColumn<CardItems, String> cardText(String id, String title,
                                                    Function<CardItems, String> value, double width) {
        TableColumn<CardItems, String> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(feature -> new ReadOnlyObjectWrapper<>(value.apply(feature.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        return column;
    }

    private void buildActions() {
        btnRefresh.setOnAction(event -> loadCandidates());
        comboGroupBy.setOnAction(event -> loadCandidates());
        btnSetTarget.setOnAction(event -> chooseTarget());
        btnPreview.setOnAction(event -> showPreview());
        btnMerge.setOnAction(event -> mergeSelected());
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private void loadCandidates() {
        MergeGroupBy groupBy = comboGroupBy.getValue() == null ? MergeGroupBy.NAME : comboGroupBy.getValue();
        setBusy(true);
        Task<List<ItemMergeCandidate>> task = new Task<>() {
            @Override
            protected List<ItemMergeCandidate> call() throws DaoException {
                return mergeService.candidates(groupBy, LIMIT);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            candidates.setAll(task.getValue());
            operations.clear();
            // The target is a row of the list that was just replaced, and keeping a stale
            // one is how a user merges into an item they can no longer see.
            target = null;
            showTarget();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError("Failed to load merge candidates", task.getException());
        });
        run(task, "item-merge-candidates");
    }

    /** The item's card, unfiltered - every document line it has, oldest first. */
    private void loadOperations(ItemMergeCandidate candidate) {
        operations.clear();
        if (candidate == null) {
            return;
        }
        Task<List<CardItems>> task = new Task<>() {
            @Override
            protected List<CardItems> call() throws DaoException {
                return cardItemService.cardRows(candidate.id(), LocalDate.of(2000, 1, 1), LocalDate.now(), null);
            }
        };
        task.setOnSucceeded(event -> operations.setAll(task.getValue()));
        task.setOnFailed(event -> AllAlerts.handleError("Failed to load the item's operations", task.getException()));
        run(task, "item-merge-operations");
    }

    // ------------------------------------------------------------------
    // Choosing, previewing, merging
    // ------------------------------------------------------------------

    private void chooseTarget() {
        ItemMergeCandidate selected = tableCandidates.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(LanguageManager.getInstance().getString("item.merge.error.select.target"));
            return;
        }
        target = selected;
        showTarget();
    }

    private void showTarget() {
        var lm = LanguageManager.getInstance();
        labelTarget.setText(target == null
                ? lm.getString("item.merge.label.target.none")
                : lm.getString("item.merge.label.target", target.name(), target.barcode()));
        labelPreview.setText(lm.getString("item.merge.hint"));
        tableCandidates.refresh();
    }

    /** The rows the user has selected, minus the target and anything the rules would refuse. */
    private List<ItemMergeCandidate> sources() {
        List<ItemMergeCandidate> sources = new ArrayList<>();
        for (ItemMergeCandidate selected : tableCandidates.getSelectionModel().getSelectedItems()) {
            if (selected != null && selected.canMergeInto(target)) {
                sources.add(selected);
            }
        }
        return sources;
    }

    private void showPreview() {
        List<ItemMergeCandidate> sources = validated();
        if (sources == null) {
            return;
        }

        setBusy(true);
        Task<List<ItemMergePreview>> task = new Task<>() {
            @Override
            protected List<ItemMergePreview> call() throws DaoException {
                List<ItemMergePreview> previews = new ArrayList<>();
                for (ItemMergeCandidate source : sources) {
                    previews.add(mergeService.preview(source.id(), target.id()));
                }
                return previews;
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            labelPreview.setText(summarise(task.getValue()));
        });
        task.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError("Failed to preview the merge", task.getException());
        });
        run(task, "item-merge-preview");
    }

    /**
     * The sentence the user checks before committing: how much history moves, and - said
     * separately because it is the one thing they might want to think twice about - how
     * much of it is dated inside a closed period.
     */
    private String summarise(List<ItemMergePreview> previews) {
        var lm = LanguageManager.getInstance();
        int documents = 0;
        int rows = 0;
        int locked = 0;
        BigDecimal opening = BigDecimal.ZERO;
        for (ItemMergePreview preview : previews) {
            documents += preview.documentLines();
            rows += preview.totalRows();
            locked += preview.lockedPeriodLines();
            opening = opening.add(preview.source().firstBalance());
        }

        String summary = lm.getString("item.merge.preview.summary",
                previews.size(), documents, rows, opening.toPlainString(), target.name());
        return locked == 0 ? summary : summary + " " + lm.getString("item.merge.preview.locked", locked);
    }

    private void mergeSelected() {
        List<ItemMergeCandidate> sources = validated();
        if (sources == null) {
            return;
        }

        var lm = LanguageManager.getInstance();
        if (!AllAlerts.confirm_all(lm.getString("item.merge.confirm.title"),
                lm.getString("item.merge.confirm.body", sources.size(), target.name()))) {
            return;
        }

        List<Integer> ids = sources.stream().map(ItemMergeCandidate::id).toList();
        int targetId = target.id();

        setBusy(true);
        Task<List<ItemMergeResult>> task = new Task<>() {
            @Override
            protected List<ItemMergeResult> call() throws DaoException {
                return mergeService.mergeAll(ids, targetId);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            List<ItemMergeResult> results = task.getValue();
            eventBus.publish(new ItemsChanged());
            AllAlerts.alertSaveWithMessage(lm.getString("item.merge.msg.done",
                    results.size(), ItemMergeResult.totalRows(results)));
            loadCandidates();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError("Failed to merge the items", task.getException());
        });
        run(task, "item-merge-execute");
    }

    /** The selected sources, or null after telling the user what is missing. */
    private List<ItemMergeCandidate> validated() {
        var lm = LanguageManager.getInstance();
        if (target == null) {
            AllAlerts.alertError(lm.getString("item.merge.error.select.target"));
            return null;
        }
        List<ItemMergeCandidate> sources = sources();
        if (sources.isEmpty()) {
            AllAlerts.alertError(lm.getString("item.merge.error.select.sources"));
            return null;
        }
        return sources;
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        btnRefresh.setDisable(busy);
        btnPreview.setDisable(busy);
        btnMerge.setDisable(busy);
        btnSetTarget.setDisable(busy);
    }

    private void run(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
