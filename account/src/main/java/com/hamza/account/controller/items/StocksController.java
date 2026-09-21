package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.StockService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The warehouses (إدارة المخازن): a name and an address, and the list they are kept in.
 * <p>
 * <b>Built in code, and rebuilt for four reasons the 2026-09-20 review listed</b>
 * ({@code docs/warehouse-plan.md} §7.14): every read and write ran on the JavaFX thread; the table
 * had no id, so its saved widths were shared with every id-less table in the package; editing and
 * deleting acted on "the selected row" from the toolbar, which needs a "choose a row first" and
 * made a row selected to read look like a row chosen to change; and deleting asked nothing - one
 * press and a warehouse was gone, when it could be. Editing and deleting are buttons in the row
 * they act on now, a delete is confirmed, and the list is read on a background {@link Task}.
 * <p>
 * A delete is usually refused, and correctly: every warehouse carries an {@code items_stock} row per
 * item from the moment it is made, and {@code DeleteRegistry.STOCKS} declares that reference. A
 * warehouse is <b>switched off</b> instead ({@code stocks.is_active}, V77): a button in its row,
 * refused by the service for the default warehouse, one still holding stock, and one with a draft
 * count open. A switched-off warehouse is marked here, never hidden - this is the one screen it is
 * switched back on from, the {@code PartySearchScope} lesson.
 * <p>
 * A warehouse's opening balances open from its row too ({@link WarehouseOpeningView}, V78): what a
 * new warehouse already holds is entered there, item by item, for the items nothing has moved in it.
 */
@FxmlPath(pathFile = "items/stocks-view.fxml")
public class StocksController {

    private static final String ACTIONS_COLUMN = "stocksActions";

    private final StockService service = ServiceRegistry.get(StockService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final TableView<Stock> table = new TableView<>();
    private final ContentSizedColumns<Stock> widths = new ContentSizedColumns<>();
    private final TextField name = new TextField();
    private final TextField address = new TextField();
    private final Label mode = new Label();
    private final Button save = new Button();
    private final Button fresh = new Button();

    /** The warehouse the form is editing, or {@code null} for a new one. */
    private Stock editing;

    @FXML
    private AnchorPane root;

    @FXML
    public void initialize() {
        Label title = new Label(text("stocks.title"));
        title.getStyleClass().add("page-title");

        name.setPromptText(text("stocks.name"));
        name.setPrefWidth(220);
        address.setPromptText(text("stocks.address"));
        address.setPrefWidth(260);
        HBox.setHgrow(address, Priority.SOMETIMES);
        save.setText(text("common.save"));
        save.getStyleClass().add("primary-button");
        save.setMinWidth(Region.USE_PREF_SIZE);
        save.setOnAction(event -> save());
        fresh.setText(text("stocks.new"));
        fresh.getStyleClass().add("neutral-button");
        fresh.setMinWidth(Region.USE_PREF_SIZE);
        fresh.setOnAction(event -> startNew());
        mode.getStyleClass().add("form-label");
        mode.setMinWidth(Region.USE_PREF_SIZE);
        // The form is filled name, then address; Enter in the address saves - one gesture per
        // warehouse, the keyboard never leaving the form.
        Utils.whenEnterPressed(name, address);
        address.setOnAction(event -> save());
        HBox form = new HBox(8, mode, name, address, fresh, save);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("app-card");
        form.setPadding(new Insets(6));
        boolean mayWrite = AuthorizationGuard.isGranted(AppPermissions.STOCK_CREATE)
                || AuthorizationGuard.isGranted(AppPermissions.STOCK_UPDATE);
        form.setDisable(!mayWrite);

        FlowPane bar = new FlowPane(8, 6);
        bar.setAlignment(Pos.CENTER_LEFT);
        new ListToolbar()
                .refresh(ListToolbar.refreshButton(this::refresh))
                .print(ListToolbar.printButton(this::print))
                .installIn(bar);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox content = new VBox(8, title, form, bar, table);
        content.setPadding(new Insets(6));
        AnchorPane.setTopAnchor(content, 0.0);
        AnchorPane.setBottomAnchor(content, 0.0);
        AnchorPane.setLeftAnchor(content, 0.0);
        AnchorPane.setRightAnchor(content, 0.0);
        root.getChildren().setAll(content);

        startNew();
        refresh();
    }

    private void buildTable() {
        // An id of its own, or TableSetting-style saved widths are shared with every id-less table
        // in the package - the stock transfer lines opened with another table's 862 points.
        table.setId("stocksTable");
        table.setPlaceholder(new Label(text("stocks.placeholder")));
        TableColumn<Stock, Void> actions = RowActionsColumn.of("stocks.transfer.column.actions",
                RowAction.permitted(List.of(
                        RowAction.of("stocks.edit", AppIcon.EDIT, "app-neutral-button",
                                AppPermissions.STOCK_UPDATE, this::edit),
                        // A switched-off warehouse holds nothing (the service refused to switch it
                        // off otherwise) and takes no new movement, an opening included.
                        new RowAction<>("stocks.opening", AppIcon.ITEM, "app-neutral-button",
                                AppPermissions.STOCK_SHOW, Stock::isActive,
                                stock -> WarehouseOpeningView.open(table.getScene().getWindow(), stock)),
                        // Two buttons, each disabled where it does not apply, rather than one whose
                        // meaning changes: the column keeps its shape as the eye runs down it.
                        new RowAction<>("stocks.deactivate", AppIcon.HIDE, "app-neutral-button",
                                AppPermissions.STOCK_UPDATE, Stock::isActive, stock -> setActive(stock, false)),
                        new RowAction<>("stocks.activate", AppIcon.SHOW, "app-neutral-button",
                                AppPermissions.STOCK_UPDATE, stock -> !stock.isActive(), stock -> setActive(stock, true)),
                        RowAction.of("stocks.delete", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.STOCK_DELETE, this::delete))));
        actions.setId(ACTIONS_COLUMN);
        TableColumn<Stock, Number> number = Columns.number("num", Stock::getId);
        number.setId("stockId");
        TableColumn<Stock, String> stockName = Columns.text("stocks.name", Stock::getName);
        stockName.setId("stockName");
        TableColumn<Stock, String> stockAddress = Columns.text("stocks.address", Stock::getAddress);
        stockAddress.setId("stockAddress");
        TableColumn<Stock, String> status = Columns.text("stocks.status",
                stock -> text(stock.isActive() ? "stocks.status.active" : "stocks.status.inactive"));
        status.setId("stockStatus");
        table.getColumns().setAll(List.of(actions, number, stockName, stockAddress, status));
        widths.install(table);
    }

    // ------------------------------------------------------------------
    // The form
    // ------------------------------------------------------------------

    private void startNew() {
        editing = null;
        name.clear();
        address.clear();
        mode.setText(text("stocks.mode.new"));
        name.requestFocus();
    }

    /** The row's own button: there is no "choose a warehouse first" to get wrong. */
    private void edit(Stock stock) {
        editing = stock;
        name.setText(stock.getName());
        address.setText(stock.getAddress());
        mode.setText(text("stocks.mode.edit", stock.getName()));
        name.requestFocus();
    }

    private void save() {
        Stock stock = editing == null ? new Stock(0, "", "") : new Stock(editing.getId(), "", "");
        stock.setName(name.getText() == null ? "" : name.getText().trim());
        stock.setAddress(address.getText() == null ? "" : address.getText().trim());
        run(() -> service.save(stock), saved -> {
            AllAlerts.alertSave();
            // Every combo offering a warehouse rereads its list (StocksChangedArchitectureTest).
            eventBus.publish(new StocksChanged());
            startNew();
            refresh();
        });
    }

    private void delete(Stock stock) {
        // A delete that is allowed is final - the warehouse and nothing else - and a mistaken press
        // used to be one click with no question. Most are refused: see the class comment.
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        run(() -> service.delete(stock.getId()), deleted -> {
            AllAlerts.alertDelete();
            eventBus.publish(new StocksChanged());
            if (editing != null && editing.getId() == stock.getId()) {
                startNew();
            }
            refresh();
        });
    }

    /**
     * Switches a warehouse off or on after asking. Switching off takes it out of every document's
     * picker; the service refuses the cases where that would strand something, with a sentence.
     */
    private void setActive(Stock stock, boolean active) {
        if (!AllAlerts.confirm_all(text(active ? "stocks.activate" : "stocks.deactivate"),
                text(active ? "stocks.confirm.activate" : "stocks.confirm.deactivate", stock.getName()))) {
            return;
        }
        run(() -> {
            service.setActive(stock.getId(), active);
            return active;
        }, done -> {
            eventBus.publish(new StocksChanged());
            refresh();
        });
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    private void refresh() {
        run(service::getStocks, stocks -> {
            table.setItems(FXCollections.observableArrayList(stocks));
            widths.layout(table);
        });
    }

    private void print() {
        if (table.getItems().isEmpty()) {
            AllAlerts.alertError(text("treasury.statement.error.print.empty"));
            return;
        }
        String title = text("stocks.report.title");
        File target = TablePdfReport.chooseTarget(table.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, title, "",
                TablePdfLayout.from(table, List.copyOf(table.getItems()), Set.of(ACTIONS_COLUMN)), () -> { });
    }

    /**
     * A database call on this screen: off the JavaFX thread, the form held while it runs, the
     * answer handed back on the JavaFX thread, a failure reported with the screen's title.
     */
    private <T> void run(Callable<T> work, Consumer<T> onDone) {
        save.setDisable(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            save.setDisable(false);
            onDone.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            save.setDisable(false);
            AllAlerts.handleError(text("stocks.title"), task.getException());
        });
        TablePdfReport.start(task, "stocks-screen");
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
