package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.config.NamesTables;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.StocksChanged;
import com.hamza.account.features.itemcard.ItemCardFilter;
import com.hamza.account.features.itemcard.ItemCardRunningBalance;
import com.hamza.account.features.itemcard.ItemCardTotals;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.interfaces.impl_dataInterface.CustomDataReturn;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersData;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersDataReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CardItems;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.CardItemService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.table.Table_Setting.column_number;

/**
 * One item's stock card: a responsive, non-blocking view of every movement in a period.
 *
 * <p>Quantities are always shown in the item's base unit. Opening and closing balances are
 * read from the database because a posted stock count can change a balance without producing
 * a document line. The rows, totals and printed extract therefore all describe the same
 * requested period rather than filtering a history already loaded in memory.</p>
 */
@Log4j2
@FxmlPath(pathFile = "items/cardItem-view.fxml")
public class CardController extends LoadData implements AppSettingInterface {

    private static final String ACTIONS_COLUMN = "item-card-actions";
    private static final String ROW_NUMBER_COLUMN = "item-card-row";
    private static final Set<String> SCREEN_ONLY_COLUMNS = Set.of(ACTIONS_COLUMN, ROW_NUMBER_COLUMN);
    private static final Set<String> MONEY_COLUMNS = Set.of(
            "item-card-price", "item-card-discount", "item-card-total");
    private static final Set<String> QUANTITY_COLUMNS = Set.of(
            "item-card-quantity", "item-card-balance");
    private static final PseudoClass DEPLETED = PseudoClass.getPseudoClass("depleted");

    private final int numItem;
    private final ItemsModel itemsModel;
    private final CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ObservableList<CardItems> rows = FXCollections.observableArrayList();
    private final ContentSizedColumns<CardItems> sizing = new ContentSizedColumns<>();

    private int stockId = DefaultStock.ID;
    private long loadToken;
    private Task<CardLoadResult> activeLoad;
    private ItemCardTotals totals = ItemCardTotals.EMPTY;
    private ItemCardFilter loadedFilter;
    private double openingBalance;
    private double closingBalance;
    private boolean balanceShown = true;

    @FXML
    private StackPane root, headerIconHost, tableHost, loadingPane;
    @FXML
    private TableView<CardItems> tableView;
    @FXML
    private ComboBox<MovementChoice> comboBox;
    @FXML
    private ComboBox<Stock> comboStock;
    @FXML
    private Label labelItemName, labelBaseUnit, labelStatus;
    @FXML
    private Label textPurchase, textSales, textRePurchase, textReSales, textCountTotals,
            textTransferIn, textTransferOut, textAdjustment;
    @FXML
    private Label textCostPurchase, textCostSales, textCostSalesRe, textCostPurchaseRe, textCostTotals;
    @FXML
    private Label textOpeningBalance, textClosingBalance;
    @FXML
    private Button btnSearch, btnPrint, btnToday, btnThisMonth, btnAllHistory;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private FlowPane toolbarRow;
    @FXML
    private VBox fromBox, toBox, stockBox, typeBox;

    public CardController(ItemsModel itemsModel, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.numItem = itemsModel.getId();
        this.itemsModel = itemsModel;
    }

    /**
     * The document family a card row belongs to, or null when the row is not a document.
     * <p>
     * A transfer and a posted count move the balance without being an invoice, so there is
     * nothing for {@code ShowInvoiceApplication} to open and the row's button is disabled
     * rather than pressed into an error - see {@link #hasDocumentToOpen}.
     */
    public static DataInterface<? extends BasePurchasesAndSales, ?, ?, ?> dataInterface(
            ProcessType processType, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        if (processType == null) return null;
        return switch (processType) {
            case PURCHASE -> new SuppliersData(daoFactory, dataPublisher);
            case PURCHASE_RETURN -> new SuppliersDataReturn(daoFactory, dataPublisher);
            case SALES -> new CustomData(daoFactory, dataPublisher);
            case SALES_RETURN -> new CustomDataReturn(daoFactory, dataPublisher);
            case TRANSFER_IN, TRANSFER_OUT, STOCK_COUNT -> null;
        };
    }

    /** Whether the row names a document a screen can open. */
    private static boolean hasDocumentToOpen(CardItems row) {
        ProcessType kind = row == null ? null : row.getProcessType();
        return kind == ProcessType.PURCHASE || kind == ProcessType.PURCHASE_RETURN
                || kind == ProcessType.SALES || kind == ProcessType.SALES_RETURN;
    }

    @FXML
    public void initialize() {
        root.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        configureHeader();
        configureTable();
        configureFilters();
        configureActions();
        subscriptions.add(eventBus.subscribe(StocksChanged.class, event -> reloadStockItems()));
        subscriptions.disposeWith(root);
        loadWholeHistory();
    }

    private void configureHeader() {
        headerIconHost.getChildren().setAll(AppIcon.ITEM.graphic(30));
        labelItemName.setText(itemsModel.getNameItem());
        labelBaseUnit.setText(itemsModel.getUnitsType().getUnit_name());
    }

    private void configureTable() {
        TableColumn<CardItems, Number> balanceColumn = named("item-card-balance",
                Columns.asQuantity(Columns.number(NamesTables.BALANCE, CardItems::getBalance)));
        tableView.getColumns().setAll(
                named(ACTIONS_COLUMN, RowActionsColumn.of("column.actions", List.of(
                        new RowAction<>("row.action.show", AppIcon.SHOW, "app-neutral-button", null,
                                CardController::hasDocumentToOpen, this::openInvoice)))),
                named(ROW_NUMBER_COLUMN, column_number()),
                named("item-card-invoice", Columns.number(NamesTables.CODE_INVOICE, CardItems::getInvoice_num)),
                named("item-card-date", Columns.date(NamesTables.DATE, CardItems::getInvoice_date)),
                named("item-card-party", Columns.text(NamesTables.NAME, CardItems::getName_account)),
                named("item-card-unit", Columns.text(NamesTables.TYPE, CardItems::getType_name)),
                named("item-card-quantity",
                        Columns.asQuantity(Columns.number(NamesTables.QUANTITY, CardItems::getQuantity))),
                named("item-card-price", Columns.asMoney(Columns.number(NamesTables.PRICE, CardItems::getPrice))),
                named("item-card-discount",
                        Columns.asMoney(Columns.number(NamesTables.DISCOUNT, CardItems::getDiscount))),
                named("item-card-total", Columns.asMoney(Columns.number(NamesTables.TOTAL, CardItems::getTotals))),
                balanceColumn,
                named("item-card-process", Columns.text(NamesTables.PROCESS_TYPE, CardItems::getProcessTypeName)),
                named("item-card-delegate", Columns.text(NamesTables.DELEGATE, CardItems::getDelegate_name))
        );

        SortedList<CardItems> sorted = new SortedList<>(rows);
        sorted.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sorted);
        tableView.setPlaceholder(new Label(text("item.card.empty")));
        tableView.setRowFactory(ignored -> depletedRow());
        tableView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                CardItems selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null) openInvoice(selected);
            }
        });

        TableSetting.tableMenuSetting(getClass(), tableView);
        sizing.install(tableView);
    }

    private TableRow<CardItems> depletedRow() {
        return new TableRow<>() {
            @Override
            protected void updateItem(CardItems item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(DEPLETED,
                        !empty && item != null && balanceShown && item.getBalance() <= 0.0);
            }
        };
    }

    private void configureFilters() {
        comboBox.getItems().setAll(
                new MovementChoice(null, text("all")),
                new MovementChoice(ProcessType.PURCHASE, text("pur")),
                new MovementChoice(ProcessType.PURCHASE_RETURN, text("RePur")),
                new MovementChoice(ProcessType.SALES, text("sales")),
                new MovementChoice(ProcessType.SALES_RETURN, text("ReSal")));
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(MovementChoice choice) {
                return choice == null ? "" : choice.label();
            }

            @Override
            public MovementChoice fromString(String value) {
                return comboBox.getValue();
            }
        });
        comboBox.getSelectionModel().selectFirst();

        comboStock.setConverter(new StringConverter<>() {
            @Override
            public String toString(Stock stock) {
                return stock == null ? "" : stock.getName();
            }

            @Override
            public Stock fromString(String value) {
                return comboStock.getValue();
            }
        });
        reloadStockItems();

        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(LocalDate.now());
        dateTo.setValue(LocalDate.now());
        whenEnterPressed(dateFrom, dateTo, comboStock, comboBox, btnSearch);

        comboBox.valueProperty().addListener((obs, oldChoice, newChoice) -> loadCard());
        comboStock.valueProperty().addListener((obs, oldStock, newStock) -> {
            if (newStock != null && newStock.getId() != stockId) {
                stockId = newStock.getId();
                loadCard();
            }
        });
    }

    private void configureActions() {
        btnSearch.setGraphic(AppIcon.SEARCH.graphic());
        btnPrint.setGraphic(AppIcon.PRINT.graphic());
        btnToday.setGraphic(AppIcon.CALENDAR.graphic());
        btnThisMonth.setGraphic(AppIcon.CALENDAR.graphic());
        btnAllHistory.setGraphic(AppIcon.HISTORY.graphic());

        // The filters were a card of their own: a title row holding the period shortcuts, then the
        // fields with search and print in the last cell. One row now, in the list screens' order.
        new ListToolbar()
                .searchField(fromBox, toBox, stockBox, typeBox)
                .search(btnSearch)
                .print(btnPrint)
                .extra(btnToday, btnThisMonth, btnAllHistory)
                .installIn(toolbarRow);
        btnSearch.setOnAction(event -> loadCard());
        btnPrint.setOnAction(event -> print());
        btnToday.setOnAction(event -> usePeriod(ItemCardFilter.today(LocalDate.now())));
        btnThisMonth.setOnAction(event -> usePeriod(ItemCardFilter.monthToDate(LocalDate.now())));
        btnAllHistory.setOnAction(event -> loadWholeHistory());
    }

    private void usePeriod(ItemCardFilter.DateRange range) {
        dateFrom.setValue(range.from());
        dateTo.setValue(range.to());
        loadCard();
    }

    /** Refreshes the small warehouse list and retains the active warehouse where possible. */
    private void reloadStockItems() {
        try {
            int keep = stockId;
            comboStock.setItems(FXCollections.observableArrayList(stockService.stocksForPicker()));
            comboStock.getItems().stream().filter(stock -> stock.getId() == keep).findFirst()
                    .or(() -> comboStock.getItems().stream()
                            .filter(stock -> stock.getId() == DefaultStock.ID).findFirst())
                    .ifPresent(comboStock.getSelectionModel()::select);
        } catch (Exception error) {
            reportError(error);
        }
    }

    private ProcessType selectedProcessType() {
        MovementChoice choice = comboBox.getValue();
        return choice == null ? null : choice.processType();
    }

    private void loadCard() {
        ItemCardFilter filter;
        try {
            filter = new ItemCardFilter(dateFrom.getValue(), dateTo.getValue(), selectedProcessType());
        } catch (IllegalArgumentException error) {
            AllAlerts.alertError(text(error.getMessage()));
            return;
        }
        startLoad(stockId, filter);
    }

    /** Loads the first movement date and the matching rows together, away from the UI thread. */
    private void loadWholeHistory() {
        int requestedStock = stockId;
        ProcessType requestedType = selectedProcessType();
        long token = beginLoad();
        Task<CardLoadResult> task = new Task<>() {
            @Override
            protected CardLoadResult call() throws Exception {
                LocalDate today = LocalDate.now();
                LocalDate first = cardItemService.firstMovementDate(requestedStock, numItem);
                ItemCardFilter.DateRange range = ItemCardFilter.wholeHistory(first, today);
                return readCard(requestedStock,
                        new ItemCardFilter(range.from(), range.to(), requestedType));
            }
        };
        start(task, token, true);
    }

    private void startLoad(int requestedStock, ItemCardFilter filter) {
        long token = beginLoad();
        Task<CardLoadResult> task = new Task<>() {
            @Override
            protected CardLoadResult call() throws Exception {
                return readCard(requestedStock, filter);
            }
        };
        start(task, token, false);
    }

    private CardLoadResult readCard(int requestedStock, ItemCardFilter filter) throws Exception {
        List<CardItems> loaded = new ArrayList<>(cardItemService.cardRows(
                requestedStock, numItem, filter.from(), filter.to(), filter.processType()));
        double opening = cardItemService.balanceBefore(requestedStock, numItem, filter.from());
        double closing = cardItemService.balanceOn(requestedStock, numItem, filter.to());
        boolean showBalance = filter.processType() == null;
        if (showBalance) ItemCardRunningBalance.apply(loaded, opening);
        return new CardLoadResult(filter, loaded, ItemCardTotals.of(loaded), opening, closing, showBalance);
    }

    private long beginLoad() {
        if (activeLoad != null) activeLoad.cancel(true);
        long token = ++loadToken;
        setBusy(true);
        return token;
    }

    private void start(Task<CardLoadResult> task, long token, boolean updateDates) {
        activeLoad = task;
        task.setOnSucceeded(event -> {
            if (token != loadToken) return;
            setBusy(false);
            CardLoadResult result = task.getValue();
            if (updateDates) {
                dateFrom.setValue(result.filter().from());
                dateTo.setValue(result.filter().to());
            }
            showResult(result);
        });
        task.setOnFailed(event -> {
            if (token != loadToken) return;
            setBusy(false);
            reportError(task.getException());
        });
        Thread thread = new Thread(task, "item-card-load-" + token);
        thread.setDaemon(true);
        thread.start();
    }

    private void showResult(CardLoadResult result) {
        balanceShown = result.balanceShown();
        openingBalance = result.openingBalance();
        closingBalance = result.closingBalance();
        totals = result.totals();
        loadedFilter = result.filter();
        rows.setAll(result.rows());
        showTotals();
        tableView.getColumns().stream()
                .filter(column -> "item-card-balance".equals(column.getId()))
                .findFirst().ifPresent(column -> column.setVisible(balanceShown));
        tableView.refresh();
        sizing.layout(tableView);
        labelStatus.setText(text("item.card.status.ready", result.rows().size()));
        btnPrint.setDisable(result.rows().isEmpty());
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        loadingPane.setVisible(busy);
        loadingPane.setManaged(busy);
        tableView.setMouseTransparent(busy);
        btnSearch.setDisable(busy);
        btnToday.setDisable(busy);
        btnThisMonth.setDisable(busy);
        btnAllHistory.setDisable(busy);
        btnPrint.setDisable(busy || rows.isEmpty());
        if (busy) labelStatus.setText(text("item.card.status.loading"));
    }

    private void showTotals() {
        textPurchase.setText(quantity(totals.purchase()));
        textSales.setText(quantity(totals.sales()));
        textRePurchase.setText(quantity(totals.purchaseReturn()));
        textReSales.setText(quantity(totals.salesReturn()));
        textTransferIn.setText(quantity(totals.transferIn()));
        textTransferOut.setText(quantity(totals.transferOut()));
        textAdjustment.setText(quantity(totals.adjustment()));
        textCountTotals.setText(quantity(totals.netQuantity()));

        textCostPurchase.setText(money(totals.costPurchase()));
        textCostSales.setText(money(totals.costSales()));
        textCostSalesRe.setText(money(totals.costSalesReturn()));
        textCostPurchaseRe.setText(money(totals.costPurchaseReturn()));
        textCostTotals.setText(money(totals.profit()));

        textOpeningBalance.setText(quantity(openingBalance));
        textClosingBalance.setText(quantity(closingBalance));
    }

    /** Prints the rows in their visible sort order and only the columns currently visible. */
    private void print() {
        if (loadedFilter == null || tableView.getItems().isEmpty()) return;
        String title = text("item.card.title");
        File target = TablePdfReport.chooseTarget(tableView.getScene().getWindow(), title);
        if (target == null) return;
        List<CardItems> displayedRows = new ArrayList<>(tableView.getItems());
        TablePdfLayout layout = TablePdfLayout.from(tableView, displayedRows,
                SCREEN_ONLY_COLUMNS, Set.of(), null,
                new TablePdfLayout.NumberFormats(MONEY_COLUMNS, QUANTITY_COLUMNS));
        String kind = loadedFilter.processType() == null
                ? text("all") : loadedFilter.processType().getType();
        String subtitle = text("item.card.print.subtitle", itemsModel.getNameItem(),
                loadedFilter.from(), loadedFilter.to(), kind);
        TablePdfReport.write(target, title, subtitle, layout, () -> { });
    }

    private void openInvoice(CardItems cardItem) {
        try {
            new ShowInvoiceApplication(dataPublisher,
                    dataInterface(cardItem.getProcessType(), daoFactory, dataPublisher),
                    daoFactory, cardItem.getInvoice_num(), cardItem.getNameItem());
        } catch (Exception error) {
            reportError(error);
        }
    }

    private static <S, T> TableColumn<S, T> named(String id, TableColumn<S, T> column) {
        column.setId(id);
        return column;
    }

    private static String quantity(double value) {
        return Columns.quantity(BigDecimal.valueOf(value));
    }

    private static String money(double value) {
        return Columns.money(BigDecimal.valueOf(value));
    }

    private static String text(String key, Object... arguments) {
        LanguageManager language = LanguageManager.getInstance();
        return arguments.length == 0 ? language.getString(key) : language.getString(key, arguments);
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return text("item.card.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void reportError(Throwable error) {
        AllAlerts.handleError(text("item.dialog.card.title"), error);
    }

    private record MovementChoice(ProcessType processType, String label) {
    }

    private record CardLoadResult(ItemCardFilter filter, List<CardItems> rows, ItemCardTotals totals,
                                  double openingBalance, double closingBalance, boolean balanceShown) {
    }
}
