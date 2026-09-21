package com.hamza.account.controller.items;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.features.documentdelete.DocumentDeleteStockCheck;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.events.StockBalancesChanged;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.stocktransfer.StockTransferCommand;
import com.hamza.account.features.stocktransfer.TransferQuantityInput;
import com.hamza.account.features.stocktransfer.StockTransferLine;
import com.hamza.account.features.stocktransfer.StockTransferService;
import com.hamza.account.features.stocktransfer.StockTransferSummary;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import lombok.extern.log4j.Log4j2;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Moves stock between warehouses (تحويل مخزني).
 * <p>
 * A line is entered in whatever unit it was counted in - a carton, a piece - and
 * converted with the item's own factor exactly as an invoice line is, so the
 * warehouse of origin's balance and the warehouse of destination's balance both
 * move by the same base-unit amount. Posting locks the source rows, refuses a
 * quantity the source cannot cover, and is refused entirely inside a closed
 * accounting period - see {@code StockTransferService}.
 * <p>
 * The history is the second tab, built by {@link StockTransferHistoryView}: a period, a
 * warehouse and a text rather than "the last two hundred", with each transfer's lines, its
 * slip and its reversal as buttons in its own row. The two tabs are two jobs - the transfer
 * being written and every transfer already posted - and neither was usable while they shared
 * one screen, first as its lower half and then as a 640-point drawer over it. Reversing
 * deletes the transfer outright, which is the only undo there is, and it lives here because
 * this is what announces that two balances moved.
 */
@Log4j2
@FxmlPath(pathFile = "items/stock-transfer-view.fxml")
public class StockTransferController {

    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockTransferService transferService = ServiceRegistry.get(StockTransferService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final ObservableList<PendingLine> lines = FXCollections.observableArrayList();

    private TextSearchApplication<ItemsModel> itemSearch;
    private StockTransferHistoryView history;

    @FXML
    private AnchorPane root;
    @FXML
    private ComboBox<Stock> comboFromStock, comboToStock;
    @FXML
    private DatePicker datePicker;
    @FXML
    private HBox itemSearchBox;
    @FXML
    private ComboBox<UnitsModel> comboUnit;
    @FXML
    private TextField txtQuantity;
    /** Why or for whom the goods moved - printed on the slip ({@code V76}). */
    @FXML
    private TextField txtNotes;
    @FXML
    private Button btnAddLine, btnRemoveLine, btnPost;
    @FXML
    private TableView<PendingLine> tableLines;
    @FXML
    private Tab historyTab;

    @FXML
    public void initialize() {
        history = new StockTransferHistoryView(transferService, this::reverse);
        historyTab.setContent(history.build());
        // Read when the tab is opened rather than when the screen is: most visits post one.
        historyTab.setOnSelectionChanged(event -> {
            if (historyTab.isSelected()) {
                history.load();
            }
        });
        buildStockCombos();
        buildItemSearch();
        buildLinesTable();
        buildActions();
        Utils.setTextFormatter(txtQuantity);
        // The column is VARCHAR(255): the field stops there, so the service's refusal of a longer
        // note is reached only by a caller that is not this screen.
        txtNotes.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= StockTransferCommand.NOTES_MAX_LENGTH ? change : null));
        // Enter moves from the unit to the quantity, and Enter in the quantity adds the line. The
        // item itself is chosen through the search dialog - the field beside it is read-only, so a
        // scanned code cannot reach this screen yet (docs/warehouse-plan.md §15).
        Utils.whenEnterPressed(comboUnit, txtQuantity);
        txtQuantity.setOnAction(event -> addLine());
        datePicker.setValue(LocalDate.now());
        // A hint, not the guard - StockTransferService.transfer asks for the key itself. The screen
        // opens on stock.transfer.show now, so a reader of the history sees a form he cannot post.
        btnPost.setDisable(!AuthorizationGuard.isGranted(AppPermissions.STOCK_TRANSFER_POST));
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private void buildStockCombos() {
        StringConverter<Stock> converter = new StringConverter<>() {
            @Override public String toString(Stock stock) { return stock == null ? "" : stock.getName(); }
            @Override public Stock fromString(String value) { return null; }
        };
        comboFromStock.setConverter(converter);
        comboToStock.setConverter(converter);
        reloadStockItems();
        subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadStockItems()));
        subscriptions.disposeWith(root);
    }

    /**
     * (Re)reads the warehouse list for both combos, keeping whichever selections are
     * still valid - a warehouse created after this screen was built is otherwise never
     * offered, since {@code ItemsButtons} constructs it once per session.
     */
    private void reloadStockItems() {
        try {
            Integer keepFrom = comboFromStock.getValue() == null ? null : comboFromStock.getValue().getId();
            Integer keepTo = comboToStock.getValue() == null ? null : comboToStock.getValue().getId();
            ObservableList<Stock> stocks = FXCollections.observableArrayList(stockService.stocksForPicker());
            comboFromStock.setItems(stocks);
            comboToStock.setItems(FXCollections.observableArrayList(stocks));

            comboFromStock.getItems().stream()
                    .filter(stock -> stock.getId() == (keepFrom == null ? DefaultStock.ID : keepFrom))
                    .findFirst()
                    .ifPresent(comboFromStock.getSelectionModel()::select);
            if (keepTo != null) {
                comboToStock.getItems().stream().filter(stock -> stock.getId() == keepTo).findFirst()
                        .ifPresent(comboToStock.getSelectionModel()::select);
            }
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    private void buildItemSearch() {
        try {
            itemSearch = new TextSearchApplication<>(new ItemsSearch(itemsService));
            itemSearchBox.getChildren().add(itemSearch.getPane());
            itemSearch.getTextSearchController().itemSearchPropertyProperty()
                    .addListener((observable, oldItem, newItem) -> populateUnits(newItem));
        } catch (IOException e) {
            reportFailure(e);
        }
        comboUnit.setConverter(new StringConverter<>() {
            @Override public String toString(UnitsModel unit) { return unit == null ? "" : unit.getUnit_name(); }
            @Override public UnitsModel fromString(String value) { return null; }
        });
    }

    private void populateUnits(ItemsModel item) {
        comboUnit.setItems(item == null
                ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(ItemUnits.unitsFor(item)));
        comboUnit.getSelectionModel().selectFirst();
    }

    private void buildLinesTable() {
        // An id of its own, or TableSetting keys the widths by column index under "table_",
        // which every id-less table in this package shares: this table opened with another
        // table's 862 and 751 points on its first two columns and its quantity off the edge.
        tableLines.setId("stockTransferLines");
        tableLines.setPlaceholder(new Label(message("stocks.transfer.placeholder.lines")));
        tableLines.setItems(lines);
        tableLines.getColumns().add(Columns.text("item.stockcount.column.item", PendingLine::itemName));
        tableLines.getColumns().add(Columns.text("item.column.unit", PendingLine::unitName));
        // As a quantity, not a number: 7 pieces read "7.0" - the double showing through.
        tableLines.getColumns().add(Columns.asQuantity(Columns.number("quantity", PendingLine::quantity)));
        TableSetting.tableMenuSetting(getClass(), tableLines);
    }

    private void buildActions() {
        btnAddLine.setOnAction(event -> addLine());
        btnRemoveLine.setOnAction(event -> removeSelectedLine());
        btnPost.setOnAction(event -> post());
    }

    // ------------------------------------------------------------------
    // Building the line list
    // ------------------------------------------------------------------

    private void addLine() {
        ItemsModel item = itemSearch.getTextSearchController().itemSearchPropertyProperty().get();
        if (item == null) {
            AllAlerts.alertError(message("stocks.transfer.error.select.item"));
            return;
        }
        UnitsModel unit = comboUnit.getValue();
        if (unit == null) {
            unit = ItemUnits.baseUnit(item);
        }
        double quantity = TransferQuantityInput.parse(txtQuantity.getText());
        if (quantity <= 0) {
            AllAlerts.alertError(message("stocks.transfer.error.invalid.quantity"));
            return;
        }

        UnitsModel resolvedUnit = unit;
        lines.removeIf(pending -> pending.item().getId() == item.getId()
                                  && pending.unit().getUnit_id() == resolvedUnit.getUnit_id());
        lines.add(new PendingLine(item, resolvedUnit, quantity));
        txtQuantity.clear();
    }

    private void removeSelectedLine() {
        PendingLine selected = tableLines.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(message("msg.select.row"));
            return;
        }
        lines.remove(selected);
    }

    // ------------------------------------------------------------------
    // Posting
    // ------------------------------------------------------------------

    private void post() {
        Stock from = comboFromStock.getValue();
        Stock to = comboToStock.getValue();
        if (from == null || to == null) {
            AllAlerts.alertError(message("stocks.transfer.error.select.stocks"));
            return;
        }
        if (from.getId() == to.getId()) {
            AllAlerts.alertError(message("stocks.transfer.error.same.stock"));
            return;
        }
        if (lines.isEmpty()) {
            AllAlerts.alertError(message("stocks.transfer.error.no.lines"));
            return;
        }
        long transferId;
        try {
            List<StockTransferLine> commandLines = lines.stream().map(PendingLine::toLine).toList();
            transferId = transferService.transfer(new StockTransferCommand(
                    from.getId(), to.getId(), datePicker.getValue(), commandLines, currentUserId(),
                    txtNotes.getText()));
            lines.clear();
            txtNotes.clear();
            eventBus.publish(new StockBalancesChanged());
        } catch (Exception e) {
            reportFailure(e);
            return;
        }
        // One question in place of the "posted" notice it replaces: the slip is what travels with
        // the goods, so the moment they are posted is the moment it is wanted.
        if (AllAlerts.confirm_all(message("stocks.transfer.msg.posted"),
                message("stocks.transfer.confirm.print.slip", transferId))) {
            StockTransferHistoryView.printSlip(root, transferService, (int) transferId);
        }
    }

    private Integer currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? null : user.getId();
    }

    // ------------------------------------------------------------------
    // History and reversal
    // ------------------------------------------------------------------

    private void reverse(StockTransferSummary transfer) {
        if (!AllAlerts.confirm_all(message("stocks.transfer.confirm.reverse.title"),
                message("stocks.transfer.confirm.reverse.body"))) {
            return;
        }
        // What reversing would take back out of the warehouse it went to. A warning, not a
        // refusal - the same answer the totals screen gives before deleting a purchase - so it
        // is asked after the reverse is confirmed, and answering no leaves everything as it was.
        if (!confirmDestinationShortfalls(transfer)) {
            return;
        }
        try {
            transferService.delete(transfer.id());
            AllAlerts.alertDeleteWithMessage(message("stocks.transfer.msg.reversed"));
            history.load();
            eventBus.publish(new StockBalancesChanged());
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    /**
     * Asks before a reversal that would put an item below zero in the destination. Reading it is
     * a query, so a failure to read must not stop the reversal: the check is a courtesy, and what
     * refuses is the service's business.
     */
    private boolean confirmDestinationShortfalls(StockTransferSummary transfer) {
        List<DocumentDeleteStockCheck.Shortfall> shortfalls;
        try {
            shortfalls = transferService.deleteShortfalls(transfer.id());
        } catch (Exception cannotRead) {
            // Logged and passed over, never shown: a reference code in front of somebody
            // reversing a transfer reads as a refusal.
            log.error("Could not check what reversing transfer {} would do to the stock: {}",
                    transfer.id(), cannotRead.getMessage(), cannotRead);
            return true;
        }
        if (shortfalls.isEmpty()) {
            return true;
        }
        StringBuilder body = new StringBuilder(message("delete.stock.negative.header", shortfalls.size()));
        shortfalls.stream().limit(10).forEach(row -> body.append(System.lineSeparator())
                .append(message("delete.stock.negative.line", row.itemName(), row.stockName(),
                        Columns.quantity(BigDecimal.valueOf(row.remainingBase())))));
        return AllAlerts.confirm_all(message("confirm"), body.toString());
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    private void reportFailure(Throwable error) {
        AllAlerts.handleError(message("setting.store.transfers"), error);
    }

    /** A line not yet posted: what the search and the unit combo resolved, and the quantity typed. */
    private record PendingLine(ItemsModel item, UnitsModel unit, double quantity) {
        String itemName() {
            return item.getNameItem();
        }

        String unitName() {
            return unit.getUnit_name();
        }

        StockTransferLine toLine() {
            return new StockTransferLine(item.getId(), quantity, unit.getUnit_id(), ItemUnits.factor(unit));
        }
    }
}
