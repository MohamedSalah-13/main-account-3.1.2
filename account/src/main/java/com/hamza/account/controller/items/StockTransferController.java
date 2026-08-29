package com.hamza.account.controller.items;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.stocktransfer.StockTransferCommand;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.io.IOException;
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
 * The lower half is the history a posted transfer can be found and reversed from:
 * reversing deletes the transfer outright, which is the only undo there is.
 */
@FxmlPath(pathFile = "items/stock-transfer-view.fxml")
public class StockTransferController {

    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockTransferService transferService = ServiceRegistry.get(StockTransferService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final ObservableList<PendingLine> lines = FXCollections.observableArrayList();
    private final ObservableList<StockTransferSummary> history = FXCollections.observableArrayList();

    private TextSearchApplication<ItemsModel> itemSearch;

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
    @FXML
    private Button btnAddLine, btnRemoveLine, btnPost, btnRefreshHistory, btnReverse;
    @FXML
    private TableView<PendingLine> tableLines;
    @FXML
    private TableView<StockTransferSummary> tableHistory;

    @FXML
    public void initialize() {
        buildStockCombos();
        buildItemSearch();
        buildLinesTable();
        buildHistoryTable();
        buildActions();
        Utils.setTextFormatter(txtQuantity);
        datePicker.setValue(LocalDate.now());
        loadHistory();
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
            ObservableList<Stock> stocks = FXCollections.observableArrayList(stockService.getStocks());
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
        tableLines.setPlaceholder(new Label(message("stocks.transfer.placeholder.lines")));
        tableLines.setItems(lines);
        tableLines.getColumns().add(Columns.text("item.stockcount.column.item", PendingLine::itemName));
        tableLines.getColumns().add(Columns.text("item.column.unit", PendingLine::unitName));
        tableLines.getColumns().add(Columns.number("quantity", PendingLine::quantity));
        TableSetting.tableMenuSetting(getClass(), tableLines);
    }

    private void buildHistoryTable() {
        tableHistory.setPlaceholder(new Label(message("stocks.transfer.placeholder.history")));
        tableHistory.setItems(history);
        tableHistory.getColumns().add(Columns.number("stocks.transfer.history.column.id", StockTransferSummary::id));
        tableHistory.getColumns().add(Columns.date("stocks.transfer.history.column.date", StockTransferSummary::transferDate));
        tableHistory.getColumns().add(Columns.text("stocks.transfer.history.column.from", StockTransferSummary::fromStockName));
        tableHistory.getColumns().add(Columns.text("stocks.transfer.history.column.to", StockTransferSummary::toStockName));
        tableHistory.getColumns().add(Columns.number("stocks.transfer.history.column.lines", StockTransferSummary::lineCount));
        TableSetting.tableMenuSetting(getClass(), tableHistory);
    }

    private void buildActions() {
        btnAddLine.setOnAction(event -> addLine());
        btnRemoveLine.setOnAction(event -> removeSelectedLine());
        btnPost.setOnAction(event -> post());
        btnRefreshHistory.setOnAction(event -> loadHistory());
        btnReverse.setOnAction(event -> reverseSelected());
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
        double quantity = parseQuantity(txtQuantity.getText());
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

    private double parseQuantity(String text) {
        try {
            return Double.parseDouble(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
        try {
            List<StockTransferLine> commandLines = lines.stream().map(PendingLine::toLine).toList();
            transferService.transfer(new StockTransferCommand(
                    from.getId(), to.getId(), datePicker.getValue(), commandLines, currentUserId()));
            AllAlerts.alertSaveWithMessage(message("stocks.transfer.msg.posted"));
            lines.clear();
            loadHistory();
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    private Integer currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? null : user.getId();
    }

    // ------------------------------------------------------------------
    // History and reversal
    // ------------------------------------------------------------------

    private void loadHistory() {
        try {
            history.setAll(transferService.recent(200));
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    private void reverseSelected() {
        StockTransferSummary selected = tableHistory.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(message("stocks.transfer.error.select.history.row"));
            return;
        }
        if (!AllAlerts.confirm_all(message("stocks.transfer.confirm.reverse.title"),
                message("stocks.transfer.confirm.reverse.body"))) {
            return;
        }
        try {
            transferService.delete(selected.id());
            AllAlerts.alertDeleteWithMessage(message("stocks.transfer.msg.reversed"));
            loadHistory();
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private String message(String key) {
        return LanguageManager.getInstance().getString(key);
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
