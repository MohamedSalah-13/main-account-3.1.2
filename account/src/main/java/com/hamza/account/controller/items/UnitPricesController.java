package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemSaved;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.unitprices.AutomaticPricing;
import com.hamza.account.features.unitprices.PriceChange;
import com.hamza.account.features.unitprices.PriceField;
import com.hamza.account.features.unitprices.UnitPriceDraft;
import com.hamza.account.features.unitprices.UnitPriceFilter;
import com.hamza.account.features.unitprices.UnitPriceItem;
import com.hamza.account.features.unitprices.UnitPriceLine;
import com.hamza.account.features.unitprices.UnitPricePage;
import com.hamza.account.features.unitprices.UnitPriceService;
import com.hamza.account.features.unitprices.UnitPricePolicy;
import com.hamza.account.features.unitprices.UnitPriceSaveCommand;
import com.hamza.account.features.unitprices.UnitPriceSaveResult;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.PageJumpBox;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The unit prices screen: every item sold in more than one unit, each unit beneath it, and the four
 * prices of each - editable in place, saved together.
 * <p>
 * What is decided here is only what needs a toolkit: the columns, the cells, the keys and the
 * threads. What an edit <em>means</em> - that a blank unit price is an automatic one, which cells
 * are changed, what "make automatic" does and which rows a save carries - is
 * {@link UnitPriceDraft}'s, and what a save may write is {@link UnitPriceService}'s.
 * <p>
 * <b>A tree row holds two ids, never a model.</b> The draft is the one copy of the figures; a cell
 * asks it on every paint. A row object carrying its own prices would be a second copy that the
 * first edit makes stale.
 */
@FxmlPath(pathFile = "items/unit-prices.fxml")
public final class UnitPricesController {

    private static final int PAGE_SIZE = 50;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "unit-prices-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** What a tree row points at: an item ({@code unitId} {@link PriceChange#ITEM}) or one of its units. */
    record Row(int itemId, int unitId) {
        boolean isItem() {
            return unitId == PriceChange.ITEM;
        }
    }

    private final UnitPriceService service;
    private final EventBus eventBus;
    private final Subscriptions subscriptions = new Subscriptions();
    private final UnitPriceDraft draft = new UnitPriceDraft();
    private final Set<UnitPriceDraft.UnitRef> ticked = new LinkedHashSet<>();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private final PageJumpBox pageJump = new PageJumpBox(this::goToPage);
    private final Set<Integer> openedOn;

    @FXML private StackPane root;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<UnitPriceFilter.PriceState> comboState;
    @FXML private CheckBox checkBelowCost;
    @FXML private ProgressIndicator progress;
    @FXML private Button btnExpandAll, btnCollapseAll, btnRefresh, btnAutomatic, btnDiscard, btnSave;
    @FXML private Button btnPrevious, btnNext;
    @FXML private Label labelSummary, labelStatus, labelPage;
    @FXML private HBox pagerBox, toolbarRow;
    @FXML private TreeTableView<Row> tree;

    private UnitPriceFilter filter = UnitPriceFilter.EMPTY;
    private int pageIndex;
    private int total;
    private boolean costVisible;
    private boolean busy;
    /** Discards the answer to a read the operator has already replaced with another. */
    private long generation;
    /** Set when the prices changed elsewhere while this screen held unsaved edits. */
    private boolean stale;

    public UnitPricesController() {
        this(Set.of());
    }

    /** Opens on these items only - the rows ticked on the items screen. Empty for every item. */
    public UnitPricesController(Set<Integer> itemIds) {
        this.service = ServiceRegistry.get(UnitPriceService.class);
        this.eventBus = ServiceRegistry.get(EventBus.class);
        this.openedOn = itemIds == null ? Set.of() : Set.copyOf(itemIds);
    }

    @FXML
    public void initialize() {
        costVisible = service != null && service.costVisible();
        filter = UnitPriceFilter.EMPTY.withItemIds(openedOn);
        configureFilters();
        configureTree();
        configureActions();
        configureEvents();
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        pagerBox.getChildren().add(pageJump);
        load();
        subscriptions.disposeWith(root);
    }

    /** Whether the window may close: nothing unsaved, or the operator agrees to lose it. */
    public boolean confirmClose() {
        return !draft.hasChanges()
                || AllAlerts.confirm_all(text("unit.prices.title"), text("unit.prices.confirm.close"));
    }

    // ---------------------------------------------------------------------------
    // Filters and paging
    // ---------------------------------------------------------------------------

    private void configureFilters() {
        comboState.setItems(FXCollections.observableArrayList(UnitPriceFilter.PriceState.values()));
        comboState.setConverter(new StringConverter<>() {
            @Override
            public String toString(UnitPriceFilter.PriceState state) {
                if (state == null) return "";
                return switch (state) {
                    case ANY -> text("unit.prices.filter.state.any");
                    case MANUAL -> text("unit.prices.filter.state.manual");
                    case AUTOMATIC -> text("unit.prices.filter.state.automatic");
                };
            }

            @Override
            public UnitPriceFilter.PriceState fromString(String string) {
                return null;
            }
        });
        comboState.setValue(UnitPriceFilter.PriceState.ANY);
        // Below cost compares with costs, which a reader without that permission is not given.
        checkBelowCost.setVisible(costVisible);
        checkBelowCost.setManaged(costVisible);

        txtSearch.textProperty().addListener((observable, old, value) -> {
            searchDelay.setOnFinished(event -> applyFilter(filter.withSearch(value)));
            searchDelay.playFromStart();
        });
        comboState.valueProperty().addListener((observable, old, state) -> applyFilter(filter.withState(state)));
        checkBelowCost.selectedProperty().addListener((observable, old, on) -> applyFilter(filter.withBelowCostOnly(on)));
        // A scanner ends its read with an Enter: the read is a search, and the Enter puts the
        // operator in the table on what it found.
        whenEnterPressed(txtSearch, tree);
    }

    private void applyFilter(UnitPriceFilter next) {
        filter = next;
        pageIndex = 0;
        load();
    }

    private void goToPage(int page) {
        if (draft.hasChanges()) return;
        pageIndex = Math.max(0, Math.min(page, pageCount() - 1));
        load();
    }

    private int pageCount() {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void load() {
        if (service == null) return;
        long request = ++generation;
        UnitPriceFilter requested = filter;
        int offset = pageIndex * PAGE_SIZE;
        Task<UnitPricePage> task = new Task<>() {
            @Override
            protected UnitPricePage call() throws Exception {
                return service.page(requested, PAGE_SIZE, offset);
            }
        };
        setBusy(true);
        task.setOnSucceeded(event -> {
            if (request != generation) return;
            setBusy(false);
            UnitPricePage page = task.getValue();
            total = page.total();
            // Past the end - the filter narrowed while on page nine - lands on the last page
            // rather than on an empty one that says there are matches.
            if (page.items().isEmpty() && total > 0 && pageIndex > 0) {
                pageIndex = pageCount() - 1;
                load();
                return;
            }
            stale = false;
            ticked.clear();
            draft.load(page.items());
            buildTree();
            refreshState(null);
        });
        task.setOnFailed(event -> {
            if (request != generation) return;
            setBusy(false);
            AllAlerts.handleError(text("unit.prices.title"), task.getException());
        });
        WORKER.submit(task);
    }

    // ---------------------------------------------------------------------------
    // The tree
    // ---------------------------------------------------------------------------

    private void configureTree() {
        tree.setShowRoot(false);
        tree.setEditable(true);
        tree.getSelectionModel().setCellSelectionEnabled(true);
        tree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tree.setPlaceholder(new Label(text("unit.prices.empty")));

        TreeTableColumn<Row, Row> select = rowColumn("", 44);
        select.setId("unit_prices_select");
        select.setMaxWidth(44);
        select.setSortable(false);
        select.setCellFactory(column -> new TickCell());

        TreeTableColumn<Row, String> name = textColumn("unit.prices.column.name", this::nameOf);
        name.setId("unit_prices_name");
        name.setPrefWidth(320);
        TreeTableColumn<Row, String> code = textColumn("unit.prices.column.code", this::codeOf);
        code.setId("unit_prices_code");
        code.setPrefWidth(120);
        TreeTableColumn<Row, String> factor = textColumn("unit.prices.column.factor", this::factorOf);
        factor.setId("unit_prices_factor");
        factor.setPrefWidth(130);

        List<TreeTableColumn<Row, ?>> columns = new ArrayList<>(List.of(select, name, code, factor));
        for (PriceField field : priceFields()) {
            columns.add(priceColumn(field));
        }
        TreeTableColumn<Row, String> state = textColumn("unit.prices.column.state", this::stateOf);
        state.setId("unit_prices_state");
        state.setPrefWidth(110);
        TreeTableColumn<Row, Row> actions = rowColumn(text("unit.prices.column.actions"), 150);
        actions.setId("unit_prices_actions");
        actions.setSortable(false);
        actions.setCellFactory(column -> new ActionCell());
        columns.add(state);
        columns.add(actions);

        tree.getColumns().setAll(columns);
        tree.setTreeColumn(name);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setRowFactory(view -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("unit-prices-item-row");
                if (!empty && row != null && row.isItem()) getStyleClass().add("unit-prices-item-row");
            }
        });

        // Enter or F2 opens the focused price; the editor's own Enter commits and moves down, so a
        // column of prices is typed as a column.
        tree.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.F2) return;
            var focused = tree.getFocusModel().getFocusedCell();
            if (focused == null || focused.getRow() < 0 || focused.getTableColumn() == null) return;
            if (focused.getTableColumn().isEditable()) {
                tree.edit(focused.getRow(), focused.getTableColumn());
                event.consume();
            }
        });
    }

    /** The cost column exists only for a reader who may see costs - removed, not hidden. */
    private List<PriceField> priceFields() {
        List<PriceField> fields = new ArrayList<>(List.of(PriceField.values()));
        if (!costVisible) fields.remove(PriceField.BUY);
        return fields;
    }

    private void buildTree() {
        TreeItem<Row> top = new TreeItem<>(new Row(0, PriceChange.ITEM));
        for (UnitPriceItem item : draft.items()) {
            TreeItem<Row> branch = new TreeItem<>(new Row(item.id(), PriceChange.ITEM));
            for (UnitPriceLine unit : item.units()) {
                branch.getChildren().add(new TreeItem<>(new Row(item.id(), unit.unitId())));
            }
            branch.setExpanded(true);
            top.getChildren().add(branch);
        }
        tree.setRoot(top);
    }

    private TreeTableColumn<Row, String> textColumn(String titleKey, Function<Row, String> value) {
        TreeTableColumn<Row, String> column = new TreeTableColumn<>(text(titleKey));
        column.setEditable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue() == null ? "" : value.apply(cell.getValue().getValue())));
        return column;
    }

    private TreeTableColumn<Row, Row> rowColumn(String title, double width) {
        TreeTableColumn<Row, Row> column = new TreeTableColumn<>(title);
        column.setEditable(false);
        column.setPrefWidth(width);
        column.setMinWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue() == null ? null : cell.getValue().getValue()));
        return column;
    }

    private TreeTableColumn<Row, Row> priceColumn(PriceField field) {
        TreeTableColumn<Row, Row> column = new TreeTableColumn<>(switch (field) {
            case BUY -> text("unit.prices.column.buy");
            case SELL_1 -> text("unit.prices.column.sell1");
            case SELL_2 -> text("unit.prices.column.sell2");
            case SELL_3 -> text("unit.prices.column.sell3");
        });
        column.setId("unit_prices_" + field.name().toLowerCase());
        column.setPrefWidth(120);
        column.setSortable(false);
        column.setEditable(service != null && (service.canEditItems() || service.canEditUnits()));
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue() == null ? null : cell.getValue().getValue()));
        column.setCellFactory(c -> new PriceCell(field));
        return column;
    }

    private String nameOf(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null) return "";
        if (row.isItem()) return item.name();
        UnitPriceLine unit = item.unit(row.unitId());
        return unit == null ? "" : unit.unitName();
    }

    private String codeOf(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null || !row.isItem()) return "";
        return item.barcode() == null || item.barcode().isBlank()
                ? String.valueOf(item.id()) : item.id() + " / " + item.barcode();
    }

    /** "12 قطعة" under a carton: how many of the item's own unit it holds. */
    private String factorOf(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null) return "";
        String base = item.baseUnitName() == null ? "" : item.baseUnitName();
        if (row.isItem()) return "1 " + base;
        UnitPriceLine unit = item.unit(row.unitId());
        return unit == null ? "" : Columns.quantity(BigDecimal.valueOf(unit.factor())) + " " + base;
    }

    private String stateOf(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null || row.isItem()) return "";
        UnitPriceLine unit = item.unit(row.unitId());
        if (unit == null) return "";
        int manual = 0;
        List<PriceField> fields = priceFields();
        for (PriceField field : fields) {
            if (!unit.isAutomatic(field)) manual++;
        }
        if (manual == 0) return text("unit.prices.state.automatic");
        return manual == fields.size() ? text("unit.prices.state.manual") : text("unit.prices.state.partial");
    }

    // ---------------------------------------------------------------------------
    // Cells
    // ---------------------------------------------------------------------------

    /** A tick on a unit row names that unit; a tick on an item row names all of its units. */
    private final class TickCell extends TreeTableCell<Row, Row> {
        private final CheckBox box = new CheckBox();

        TickCell() {
            box.setFocusTraversable(false);
            box.setOnAction(event -> {
                Row row = getItem();
                if (row == null) return;
                for (UnitPriceDraft.UnitRef ref : unitsOf(row)) {
                    if (box.isSelected()) ticked.add(ref);
                    else ticked.remove(ref);
                }
                tree.refresh();
                refreshState(null);
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            List<UnitPriceDraft.UnitRef> refs = unitsOf(row);
            box.setSelected(!refs.isEmpty() && ticked.containsAll(refs));
            setGraphic(box);
        }
    }

    private List<UnitPriceDraft.UnitRef> unitsOf(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null) return List.of();
        if (!row.isItem()) return List.of(new UnitPriceDraft.UnitRef(row.itemId(), row.unitId()));
        return item.units().stream().map(unit -> new UnitPriceDraft.UnitRef(item.id(), unit.unitId())).toList();
    }

    /**
     * One price. On an item row it is the item's own price. On a unit row it is what the unit sells
     * for: its own price where it has one, and otherwise the item's times the factor, shown in the
     * automatic style. Editing a unit's price opens on its own figure - blank when it has none - and
     * clearing it is how a single price is made automatic again.
     */
    private final class PriceCell extends TreeTableCell<Row, Row> {
        private final PriceField field;
        private final NumberTextConverter converter = NumberTextConverter.money();
        private TextField editor;

        PriceCell(PriceField field) {
            this.field = field;
            getStyleClass().add("unit-price-cell");
        }

        @Override
        public void startEdit() {
            Row row = getItem();
            if (row == null || !mayEdit(row) || busy) return;
            super.startEdit();
            if (!isEditing()) return;
            editor = new TextField(editorText(row));
            editor.setOnAction(event -> {
                commitText(row, editor.getText());
                event.consume();
            });
            editor.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) cancelEdit();
            });
            editor.focusedProperty().addListener((observable, was, now) -> {
                if (!now && isEditing()) commitText(row, editor.getText());
            });
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            editor = null;
            paint(getItem());
        }

        private void commitText(Row row, String typed) {
            BigDecimal value;
            try {
                value = NumberTextConverter.parse(typed);
            } catch (NumberFormatException notANumber) {
                refuse();
                return;
            }
            double amount = value == null ? 0 : value.doubleValue();
            if (UnitPricePolicy.outOfRange(amount)) {
                refuse();
                return;
            }
            if (row.isItem()) draft.setItemPrice(row.itemId(), field, amount);
            else draft.setUnitPrice(row.itemId(), row.unitId(), field, amount);
            int index = getIndex();
            TreeTableColumn<Row, Row> column = getTableColumn();
            super.cancelEdit();
            editor = null;
            tree.refresh();
            refreshState(null);
            // Down one row, same column, ready for the next Enter.
            Platform.runLater(() -> {
                int next = Math.min(index + 1, tree.getExpandedItemCount() - 1);
                tree.getSelectionModel().clearAndSelect(next, column);
                tree.getFocusModel().focus(next, column);
                tree.scrollTo(Math.max(0, next - 3));
                tree.requestFocus();
            });
        }

        private void refuse() {
            refreshState("unit.prices.error.range");
            if (editor != null) editor.selectAll();
        }

        private String editorText(Row row) {
            UnitPriceItem item = draft.item(row.itemId());
            if (item == null) return "";
            double value = row.isItem() ? item.prices().get(field) : ownOf(item, row, field);
            return value > 0 ? converter.toString(value) : "";
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("unit-price-automatic", "unit-price-changed", "unit-price-below-cost");
                setTooltip(null);
                return;
            }
            if (isEditing() && editor != null) {
                setGraphic(editor);
                setText(null);
                return;
            }
            paint(row);
        }

        private void paint(Row row) {
            setGraphic(null);
            getStyleClass().removeAll("unit-price-automatic", "unit-price-changed", "unit-price-below-cost");
            setTooltip(null);
            UnitPriceItem item = row == null ? null : draft.item(row.itemId());
            if (item == null) {
                setText(null);
                return;
            }
            double shown;
            if (row.isItem()) {
                shown = item.prices().get(field);
            } else {
                UnitPriceLine unit = item.unit(row.unitId());
                if (unit == null) {
                    setText(null);
                    return;
                }
                shown = unit.effective(field, item.prices());
                if (unit.isAutomatic(field)) {
                    getStyleClass().add("unit-price-automatic");
                    setTooltip(new Tooltip(text("unit.prices.tooltip.automatic")));
                }
                if (costVisible && field.isSell() && shown > 0 && shown <= unit.effective(PriceField.BUY, item.prices())) {
                    getStyleClass().add("unit-price-below-cost");
                    setTooltip(new Tooltip(text("unit.prices.tooltip.below.cost")));
                }
            }
            if (draft.isChanged(row.itemId(), row.unitId(), field)) getStyleClass().add("unit-price-changed");
            // A tier nobody priced is empty, not "0.00" - a zero would read as a free sale.
            setText(shown > 0 ? converter.toString(shown) : "");
        }
    }

    private static double ownOf(UnitPriceItem item, Row row, PriceField field) {
        UnitPriceLine unit = item.unit(row.unitId());
        return unit == null ? 0 : unit.own().get(field);
    }

    private boolean mayEdit(Row row) {
        if (service == null) return false;
        return row.isItem() ? service.canEditItems() : service.canEditUnits();
    }

    /** Per unit: make every price automatic. Per item: make every price of all its units automatic. */
    private final class ActionCell extends TreeTableCell<Row, Row> {
        private final Button button = new Button();

        ActionCell() {
            button.getStyleClass().add("neutral-button");
            button.setGraphic(AppIcon.REFRESH.graphic(12));
            button.setFocusTraversable(false);
            button.setOnAction(event -> {
                Row row = getItem();
                if (row == null) return;
                draft.applyPricing(unitsOf(row), EnumSet.copyOf(priceFields()), AutomaticPricing.Mode.AUTOMATIC);
                tree.refresh();
                refreshState(null);
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            button.setText(row.isItem() ? text("unit.prices.action.item.automatic") : text("unit.prices.action.unit.automatic"));
            button.setDisable(busy || service == null || !service.canEditUnits() || allAutomatic(row));
            setGraphic(button);
        }
    }

    private boolean allAutomatic(Row row) {
        UnitPriceItem item = draft.item(row.itemId());
        if (item == null) return true;
        for (UnitPriceDraft.UnitRef ref : unitsOf(row)) {
            UnitPriceLine unit = item.unit(ref.unitId());
            if (unit == null) continue;
            for (PriceField field : priceFields()) {
                if (!unit.isAutomatic(field)) return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------

    private void configureActions() {
        btnRefresh.setGraphic(AppIcon.REFRESH.graphic());
        btnExpandAll.setGraphic(AppIcon.TREE.graphic());
        btnCollapseAll.setGraphic(AppIcon.CLEAR.graphic());
        btnSave.setGraphic(AppIcon.SAVE.graphic());
        btnDiscard.setGraphic(AppIcon.CLEAR.graphic());
        btnAutomatic.setGraphic(AppIcon.REFRESH.graphic());
        btnAutomatic.setTooltip(new Tooltip(text("unit.prices.automatic.tooltip")));

        btnRefresh.setOnAction(event -> load());
        // Search and its two filters, then refresh, then this editor's own tree controls. Refresh
        // used to come last, after expanding and collapsing.
        new ListToolbar()
                .searchField(txtSearch, comboState, checkBelowCost)
                .refresh(btnRefresh)
                .extra(btnExpandAll, btnCollapseAll, progress)
                .installIn(toolbarRow);
        btnExpandAll.setOnAction(event -> setExpanded(true));
        btnCollapseAll.setOnAction(event -> setExpanded(false));
        btnPrevious.setOnAction(event -> goToPage(pageIndex - 1));
        btnNext.setOnAction(event -> goToPage(pageIndex + 1));
        btnDiscard.setOnAction(event -> {
            draft.discard();
            tree.refresh();
            refreshState(null);
            if (stale) load();
        });
        btnSave.setOnAction(event -> save());
        btnAutomatic.setOnAction(event -> openAutomaticPricing());
    }

    private void setExpanded(boolean expanded) {
        if (tree.getRoot() == null) return;
        for (TreeItem<Row> branch : tree.getRoot().getChildren()) branch.setExpanded(expanded);
    }

    private void save() {
        if (busy || !draft.hasChanges() || service == null) return;
        UnitPriceSaveCommand command = draft.command(CurrentUser.get().getId());
        Task<UnitPriceSaveResult> task = new Task<>() {
            @Override
            protected UnitPriceSaveResult call() throws Exception {
                return service.save(command);
            }
        };
        setBusy(true);
        task.setOnSucceeded(event -> {
            setBusy(false);
            // The save announced the change to the other tills; this till's own screens hear it here.
            if (eventBus != null) eventBus.publish(new ItemsChanged());
            refreshStatusAfterSave(task.getValue());
            load();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            refreshState(null);
            AllAlerts.handleError(text("unit.prices.title"), task.getException());
        });
        WORKER.submit(task);
    }

    private void refreshStatusAfterSave(UnitPriceSaveResult result) {
        labelStatus.setText(LanguageManager.getInstance().getString("unit.prices.status.saved", result.total()));
    }

    private void openAutomaticPricing() {
        if (busy || service == null || !service.canEditUnits()) return;
        UnitPricingDialog dialog = new UnitPricingDialog(root.getScene() == null ? null : root.getScene().getWindow(),
                costVisible, ticked.size(), draft.allUnits().size(), total, !draft.hasChanges());
        dialog.showAndWait().ifPresent(options -> {
            switch (options.scope()) {
                case TICKED -> applyOnScreen(List.copyOf(ticked), options);
                case PAGE -> applyOnScreen(draft.allUnits(), options);
                case FILTER -> applyToFilter(options);
            }
        });
    }

    /** The loaded rows change on screen and wait for Save, marked like any other edit. */
    private void applyOnScreen(List<UnitPriceDraft.UnitRef> targets, UnitPricingDialog.Options options) {
        List<UnitPriceDraft.PreviewRow> preview = draft.preview(targets, options.fields(), options.mode());
        if (preview.isEmpty()) {
            refreshState("unit.prices.automatic.nothing");
            return;
        }
        if (!UnitPricingDialog.confirmPreview(root.getScene().getWindow(), preview, false)) return;
        draft.applyPricing(targets, options.fields(), options.mode());
        tree.refresh();
        refreshState(null);
    }

    /**
     * Every item the filter matches, across every page: read, previewed, and saved straight away
     * after the operator confirms. The screen holds no edits while this runs - the dialog does not
     * offer it otherwise - so what is saved is exactly what was previewed.
     */
    private void applyToFilter(UnitPricingDialog.Options options) {
        UnitPriceFilter requested = filter;
        Task<List<UnitPriceItem>> read = new Task<>() {
            @Override
            protected List<UnitPriceItem> call() throws Exception {
                return service.all(requested);
            }
        };
        setBusy(true);
        read.setOnSucceeded(event -> {
            setBusy(false);
            UnitPriceDraft wide = new UnitPriceDraft();
            wide.load(read.getValue());
            List<UnitPriceDraft.PreviewRow> preview = wide.preview(wide.allUnits(), options.fields(), options.mode());
            if (preview.isEmpty()) {
                refreshState("unit.prices.automatic.nothing");
                return;
            }
            if (!UnitPricingDialog.confirmPreview(root.getScene().getWindow(), preview, true)) return;
            wide.applyPricing(wide.allUnits(), options.fields(), options.mode());
            UnitPriceSaveCommand command = wide.command(CurrentUser.get().getId());
            Task<UnitPriceSaveResult> write = new Task<>() {
                @Override
                protected UnitPriceSaveResult call() throws Exception {
                    return service.save(command);
                }
            };
            setBusy(true);
            write.setOnSucceeded(done -> {
                setBusy(false);
                if (eventBus != null) eventBus.publish(new ItemsChanged());
                refreshStatusAfterSave(write.getValue());
                load();
            });
            write.setOnFailed(failed -> {
                setBusy(false);
                AllAlerts.handleError(text("unit.prices.title"), write.getException());
            });
            WORKER.submit(write);
        });
        read.setOnFailed(event -> {
            setBusy(false);
            AllAlerts.handleError(text("unit.prices.title"), read.getException());
        });
        WORKER.submit(read);
    }

    private void configureEvents() {
        if (eventBus == null) return;
        subscriptions.add(eventBus.subscribe(ItemsChanged.class, event -> changedElsewhere()));
        subscriptions.add(eventBus.subscribe(ItemSaved.class, event -> changedElsewhere()));
    }

    /**
     * Prices moved somewhere else. With nothing unsaved the page is simply read again; with edits
     * pending it is not - reloading would throw them away - and the screen says so instead. The save
     * itself still refuses any figure that moved underneath it.
     */
    private void changedElsewhere() {
        if (busy) return;
        if (!draft.hasChanges()) {
            load();
            return;
        }
        stale = true;
        refreshState(null);
    }

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        refreshState(null);
    }

    /**
     * Redraws everything that depends on the draft and the page. Unsaved edits lock the search, the
     * filters and the pager: moving to another page would read over them, and a pending edit that
     * silently vanished is worse than a control that says "save or discard first".
     */
    private void refreshState(String messageKey) {
        boolean pending = draft.hasChanges();
        int changed = draft.changedRowCount();
        LanguageManager lm = LanguageManager.getInstance();

        labelSummary.setText(lm.getString("unit.prices.summary", total, draft.items().size()));
        if (messageKey != null) {
            labelStatus.setText(text(messageKey));
        } else if (stale) {
            labelStatus.setText(text("unit.prices.status.stale"));
        } else if (pending) {
            labelStatus.setText(lm.getString("unit.prices.status.pending", changed));
        } else if (!ticked.isEmpty()) {
            labelStatus.setText(lm.getString("unit.prices.status.ticked", ticked.size()));
        } else if (busy) {
            labelStatus.setText(text("unit.prices.status.loading"));
        }

        boolean locked = busy || pending;
        txtSearch.setDisable(busy || pending);
        comboState.setDisable(locked);
        checkBelowCost.setDisable(locked);
        btnRefresh.setDisable(locked);
        btnPrevious.setDisable(locked || pageIndex <= 0);
        btnNext.setDisable(locked || pageIndex >= pageCount() - 1);
        pageJump.setDisable(locked);
        btnSave.setDisable(busy || !pending);
        btnDiscard.setDisable(busy || !pending);
        btnAutomatic.setDisable(busy || service == null || !service.canEditUnits() || draft.items().isEmpty());
        labelPage.setText(lm.getString("unit.prices.page", pageIndex + 1, pageCount()));
        pageJump.showing(pageIndex, pageCount());
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
