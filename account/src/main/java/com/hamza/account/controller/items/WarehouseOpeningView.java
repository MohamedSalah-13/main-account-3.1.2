package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.StockBalancesChanged;
import com.hamza.account.features.stockopening.WarehouseOpeningDraft;
import com.hamza.account.features.stockopening.WarehouseOpeningFilter;
import com.hamza.account.features.stockopening.WarehouseOpeningPage;
import com.hamza.account.features.stockopening.WarehouseOpeningRow;
import com.hamza.account.features.stockopening.WarehouseOpeningService;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * One warehouse's opening balances (أرصدة أول المدة): its items, each with the opening it holds, and
 * a figure typed in for every item nothing has moved there yet.
 * <p>
 * Opened from the warehouse's row on the warehouses screen - the gesture of a shop giving a new
 * warehouse what is already on its shelves. It holds no rule: which rows take a figure is
 * {@link WarehouseOpeningDraft#editable}, what is typed and not saved is the draft's, and whether it
 * may be written is decided again by {@link WarehouseOpeningService} inside its transaction.
 * <p>
 * <b>The scanner's path.</b> A code scanned into the search box ends with an Enter; that searches,
 * and when it finds the one item the quantity cell opens on it. The quantity's own Enter commits it
 * and puts the caret back in the search box with its text selected, so the next scan replaces it -
 * scan, type, scan, type, with no mouse between two items. A scan that finds nothing to type into -
 * an item that has moved here - leaves its code selected too, for the same reason. Both halves were
 * found wanting on screen, not by a test: see {@code docs/warehouse-plan.md} §18.
 */
final class WarehouseOpeningView {

    private final WarehouseOpeningService service = ServiceRegistry.get(WarehouseOpeningService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Stock warehouse;
    private final boolean mayWrite = AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE);
    private final WarehouseOpeningDraft draft = new WarehouseOpeningDraft();

    private final TextField search = new TextField();
    private final CheckBox unmovedOnly = new CheckBox();
    private final TableView<WarehouseOpeningRow> table = new TableView<>();
    private final ContentSizedColumns<WarehouseOpeningRow> widths = new ContentSizedColumns<>();
    private final TableColumn<WarehouseOpeningRow, Double> openingColumn = new TableColumn<>();
    private final Label countLabel = new Label();
    private final Label pendingLabel = new Label();
    private final Label pageLabel = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Button save = new Button();
    private int page;

    private WarehouseOpeningView(Stock warehouse) {
        this.warehouse = warehouse;
    }

    /** Opens the window for one warehouse, beside the warehouses screen rather than over it. */
    static void open(Window owner, Stock warehouse) {
        WarehouseOpeningView view = new WarehouseOpeningView(warehouse);
        Scene scene = new Scene(view.build(), 900, 640);
        ThemeManager.apply(scene);
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(text("stock.opening.window.title", warehouse.getName()));
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        // Typed figures are asked about before the window goes, not lost with it.
        stage.setOnCloseRequest(event -> {
            if (!view.draft.isEmpty() && !AllAlerts.confirm_all(text("stock.opening.title"),
                    text("stock.opening.confirm.discard", view.draft.size()))) {
                event.consume();
            }
        });
        stage.show();
        view.reload();
        view.search.requestFocus();
    }

    private VBox build() {
        Label title = new Label(text("stock.opening.window.title", warehouse.getName()));
        title.getStyleClass().add("page-title");
        Label hint = new Label(text("stock.opening.hint"));
        hint.getStyleClass().add("form-label");
        hint.setWrapText(true);

        search.setPromptText(text("stock.opening.search.prompt"));
        search.setPrefWidth(260);
        search.setOnAction(event -> reload());
        unmovedOnly.setText(text("stock.opening.unmoved.only"));
        unmovedOnly.setMinWidth(Region.USE_PREF_SIZE);
        unmovedOnly.setOnAction(event -> reload());

        FlowPane bar = new FlowPane(8, 6);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .searchField(search, unmovedOnly)
                .search(ListToolbar.button("search", AppIcon.SEARCH, this::reload))
                .refresh(ListToolbar.refreshButton(this::load))
                .installIn(bar);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        previous.setText(text("treasury.statement.previous"));
        next.setText(text("treasury.statement.next"));
        for (Button button : List.of(previous, next)) {
            button.getStyleClass().add("app-neutral-button");
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        previous.setOnAction(event -> { page = Math.max(0, page - 1); load(); });
        next.setOnAction(event -> { page++; load(); });
        save.setText(text("common.save"));
        save.getStyleClass().add("primary-button");
        save.setMinWidth(Region.USE_PREF_SIZE);
        save.setOnAction(event -> save());
        save.setVisible(mayWrite);
        save.setManaged(mayWrite);
        for (Label label : List.of(countLabel, pendingLabel, pageLabel)) {
            label.getStyleClass().add("form-label");
        }
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox footer = new HBox(8, countLabel, pendingLabel, gap, previous, pageLabel, next, save);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(8, title, hint, bar, table, footer);
        root.setPadding(new Insets(8));
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        showPending();
        return root;
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    private void buildTable() {
        table.setId("warehouseOpeningTable");
        table.setPlaceholder(new Label(text("stock.opening.placeholder")));
        table.setEditable(mayWrite);

        TableColumn<WarehouseOpeningRow, String> code = Columns.text("stocks.transfer.column.code", WarehouseOpeningRow::code);
        code.setId("openingCode");
        TableColumn<WarehouseOpeningRow, String> name = Columns.text("item.stockcount.column.item", WarehouseOpeningRow::name);
        name.setId("openingItem");
        TableColumn<WarehouseOpeningRow, String> unit = Columns.text("stock.opening.column.unit", WarehouseOpeningRow::unitName);
        unit.setId("openingUnit");

        openingColumn.setText(text("stock.opening.column.opening"));
        openingColumn.setId("openingBalance");
        openingColumn.setEditable(true);
        openingColumn.setPrefWidth(130);
        // What the row shows is the draft's answer - a figure typed on another page is still there.
        openingColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(draft.shown(cell.getValue())));
        openingColumn.setCellFactory(column -> new OpeningCell());
        openingColumn.setOnEditCommit(event -> commit(event.getRowValue(), event.getNewValue()));

        TableColumn<WarehouseOpeningRow, String> state = Columns.text("stock.opening.column.state",
                row -> text(row.moved() ? "stock.opening.state.locked" : "stock.opening.state.open"));
        state.setId("openingState");

        table.getColumns().setAll(List.of(code, name, unit, openingColumn, state));
        widths.install(table);
    }

    /** Back to the first page - a new search describes another list. */
    private void reload() {
        page = 0;
        load();
        // A scanned code finds one item: its quantity is what is typed next.
        if (mayWrite && table.getItems().size() == 1 && search.getText() != null && !search.getText().isBlank()
                && WarehouseOpeningDraft.editable(table.getItems().getFirst())) {
            Platform.runLater(() -> {
                // The rows were just replaced, and a cell learns its new row on the next layout. Opened
                // before that, the editor was refilled underneath the operator with its text no longer
                // selected, so "12" typed over "0" went in front of it: 120 was saved. Seen on screen,
                // the second scan of a session, and confirmed in MySQL.
                table.layout();
                table.getSelectionModel().select(0);
                table.edit(0, openingColumn);
            });
        } else if (search.isFocused()) {
            // Nothing to type into - several items, none, or one that has moved here. The next scan
            // replaces this one rather than being appended to it and finding nothing.
            search.selectAll();
        }
    }

    /** The same page again. */
    private void load() {
        try {
            WarehouseOpeningPage shown = service.page(new WarehouseOpeningFilter(warehouse.getId(),
                    search.getText(), unmovedOnly.isSelected(), page, WarehouseOpeningFilter.PAGE_SIZE));
            table.setItems(FXCollections.observableArrayList(shown.rows()));
            widths.layout(table);
            page = shown.page();
            previous.setDisable(!shown.hasPrevious());
            next.setDisable(!shown.hasNext());
            pageLabel.setText(text("treasury.history.page", page + 1));
            countLabel.setText(text("stock.opening.count", shown.items()));
        } catch (Exception e) {
            AllAlerts.handleError(text("stock.opening.title"), e);
        }
    }

    // ------------------------------------------------------------------
    // Typing and saving
    // ------------------------------------------------------------------

    private void commit(WarehouseOpeningRow row, Double typed) {
        // A cell left blank arrives null and nonsense NaN; neither is "none on the shelf", which is
        // a real figure somebody can type as 0. Nor is a negative quantity an opening.
        if (row != null && typed != null && Double.isFinite(typed) && typed >= 0
                && WarehouseOpeningDraft.editable(row)) {
            draft.set(row, typed);
        }
        table.refresh();
        showPending();
        if (search.getText() != null && !search.getText().isBlank()) {
            Platform.runLater(() -> {
                search.requestFocus();
                search.selectAll();
            });
        }
    }

    private void showPending() {
        pendingLabel.setText(draft.isEmpty() ? "" : text("stock.opening.pending", draft.size()));
        save.setDisable(draft.isEmpty());
    }

    private void save() {
        try {
            int written = service.save(warehouse.getId(), draft.changes());
            draft.clear();
            showPending();
            // The service told the other machines; this one hears it here.
            eventBus.publish(new StockBalancesChanged());
            eventBus.publish(new ItemsChanged());
            AllAlerts.alertSaveWithMessage(text("stock.opening.saved", written));
            load();
        } catch (Exception e) {
            AllAlerts.handleError(text("stock.opening.title"), e);
        }
    }

    /**
     * The quantity cell: it opens only on a row that may take a figure, and a figure typed and not
     * yet saved is bold, so what the save will write is visible before it is pressed.
     */
    private final class OpeningCell extends TextFieldTableCell<WarehouseOpeningRow, Double> {

        OpeningCell() {
            // NumberTextConverter, not DoubleStringConverter: the latter does not read the ٠-٩ an
            // Arabic keyboard types - the stock count's cell learned that first.
            super(NumberTextConverter.quantity());
            setStyle("-fx-alignment: CENTER-RIGHT;");
        }

        @Override
        public void startEdit() {
            WarehouseOpeningRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (row == null || !WarehouseOpeningDraft.editable(row)) {
                return;
            }
            super.startEdit();
        }

        @Override
        public void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            WarehouseOpeningRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
            boolean changed = row != null && draft.isChanged(row.itemId());
            setStyle("-fx-alignment: CENTER-RIGHT;" + (changed ? " -fx-font-weight: bold;" : ""));
            // Refilled while open, the editor's text is replaced and its selection lost - the caret sits
            // before the old figure and what is typed is prepended to it. Whatever is typed next replaces
            // the figure, as it does when the editor first opens.
            if (isEditing() && getGraphic() instanceof TextField editor) {
                editor.selectAll();
            }
        }
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
