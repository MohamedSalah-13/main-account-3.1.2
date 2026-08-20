package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.NamesTables;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.itemcard.ItemCardRunningBalance;
import com.hamza.account.features.itemcard.ItemCardTotals;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.interfaces.impl_dataInterface.CustomDataReturn;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersData;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersDataReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.CardItemDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.CardItemService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static com.hamza.account.type.TypeList.processTypeList;
import static com.hamza.controlsfx.table.Table_Setting.column_number;

/**
 * One item's stock card: every movement of it in a period, what the period moved, and
 * what the item's balance was before and after it.
 * <p>
 * Three things about the numbers on this screen are worth knowing before changing it:
 * <ul>
 *   <li><b>Quantities are in the item's base unit.</b> A line is scaled by the factor
 *       the line itself stored, which is what {@code quantity_items_table} counts with.
 *       It used to be scaled by {@code units.value_d} - one factor for the whole
 *       database - so an item sold by the carton was counted as though every carton in
 *       the shop held the same number.</li>
 *   <li><b>The opening and closing balances come from the database, not from the
 *       rows.</b> A posted stock count moves the balance without producing a card
 *       line, so a card that derived its closing balance by adding up its own rows
 *       would disagree with every other screen after the first inventory.</li>
 *   <li><b>The period is a query, not a filter.</b> The rows are read for the dates
 *       asked for; the screen no longer loads the item's whole history to show a month
 *       of it.</li>
 * </ul>
 */
@Log4j2
@FxmlPath(pathFile = "items/cardItem-view.fxml")
public class CardController extends LoadData implements Initializable, AppSettingInterface {

    /** Enough decimals for a fractional quantity, without printing 12.000000000002. */
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##");

    private final int numItem;

    private final ItemsModel itemsModel;
    private final CardItemService cardItemService = ServiceRegistry.get(CardItemService.class);
    @FXML
    private TableView<CardItems> tableView;
    @FXML
    private ComboBox<String> comboBox;
    @FXML
    private Text textPurchase, textSales, textRePurchase, textReSales, textCountTotals, textCostPurchase, textCostSales, textCostSalesRe, textCostPurchaseRe, textCostTotals, textType, textOpeningBalance, textClosingBalance;
    @FXML
    private Label labelPurchase, labelSales, labelRePurchase, labelReSales, labelFrom, labelTo, labelType, labelName;
    @FXML
    private Button btnSearch, btnPrint;
    @FXML
    private TextField textName;
    @FXML
    private DatePicker dateFrom, dateTo;

    /** The rows behind the table, in movement order - the order the balance runs in. */
    private final ObservableList<CardItems> rows = FXCollections.observableArrayList();
    private ItemCardTotals totals = ItemCardTotals.EMPTY;
    private double openingBalance;
    private double closingBalance;
    /**
     * The period and the document kind the rows on screen were read for - not what the
     * pickers say now. Changing a date without pressing search must not print a report
     * for one period with the totals of another, which is what reading the controls at
     * print time did.
     */
    private LocalDate loadedFrom;
    private LocalDate loadedTo;
    private ProcessType loadedProcessType;
    /** Whether the rows on screen carry a running balance - see {@link #loadCard()}. */
    private boolean balanceShown = true;

    public CardController(ItemsModel itemsModel, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.numItem = itemsModel.getId();
        this.itemsModel = itemsModel;
    }

    public static DataInterface<? extends BasePurchasesAndSales, ?, ?, ?> dataInterface(ProcessType processType, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        if (processType == null) return null;
        return switch (processType) {
            case PURCHASE -> new SuppliersData(daoFactory, dataPublisher);
            case PURCHASE_RETURN -> new SuppliersDataReturn(daoFactory, dataPublisher);
            case SALES -> new CustomData(daoFactory, dataPublisher);
            case SALES_RETURN -> new CustomDataReturn(daoFactory, dataPublisher);
        };
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getTable();
        otherSetting();
        addColumnShowInvoice();
        action();
        applyRowColoringForBalance();
        loadCard();
    }

    private void getTable() {
        TableColumn<CardItems, Number> balanceColumn = Columns.number(NamesTables.BALANCE, CardItems::getBalance);
        balanceColumn.setId("balance");
        tableView.getColumns().addAll(
                Columns.number(NamesTables.CODE_INVOICE, CardItems::getInvoice_num),
                Columns.date(NamesTables.DATE, CardItems::getInvoice_date),
                Columns.text(NamesTables.NAME, CardItems::getName_account),
                Columns.text(NamesTables.TYPE, CardItems::getType_name),
                Columns.number(NamesTables.QUANTITY, CardItems::getQuantity),
                Columns.number(NamesTables.PRICE, CardItems::getPrice),
                Columns.number(NamesTables.DISCOUNT, CardItems::getDiscount),
                Columns.number(NamesTables.TOTAL, CardItems::getTotals),
                balanceColumn,
                Columns.text(NamesTables.PROCESS_TYPE, CardItems::getProcessTypeName),
                Columns.text(NamesTables.DELEGATE, CardItems::getDelegate_name)
        );
        tableView.getColumns().addFirst(column_number());
        SortedList<CardItems> sortedList = new SortedList<>(rows);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void otherSetting() {
        var lm = LanguageManager.getInstance();
        btnSearch.setText(lm.getString("search"));
        btnPrint.setText(lm.getString("print"));
        labelPurchase.setText(lm.getString("pur"));
        labelSales.setText(lm.getString("sales"));
        labelRePurchase.setText(lm.getString("RePur"));
        labelReSales.setText(lm.getString("ReSal"));
        labelFrom.setText(lm.getString("from"));
        labelTo.setText(lm.getString("to"));
        labelType.setText(lm.getString("item.card.invoice.type"));
        labelName.setText(lm.getString("column.name_item"));

        comboBox.getItems().add(lm.getString("all"));
        comboBox.getItems().addAll(processTypeList);
        comboBox.getSelectionModel().select(0);

        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(firstMovementDate());
        textType.setText(itemsModel.getUnitsType().getUnit_name());
        textName.setText(itemsModel.getNameItem());
    }

    /**
     * The card opens on the item's whole history, as it always has - but the range is
     * asked of the database rather than discovered by loading every line the item was
     * ever on. An item that has never moved opens on today.
     */
    private LocalDate firstMovementDate() {
        try {
            LocalDate first = cardItemService.firstMovementDate(numItem);
            if (first != null) return first;
        } catch (Exception e) {
            logError(e);
        }
        return LocalDate.now();
    }

    private void action() {
        var image = new Image_Setting();
        btnSearch.setGraphic(ImageChoose.createIcon(image.search));
        btnPrint.setGraphic(ImageChoose.createIcon(image.print));
        btnSearch.setOnAction(actionEvent -> loadCard());
        btnPrint.setOnAction(actionEvent -> print());
    }

    /**
     * Reads the card for the period on screen and shows what it adds up to.
     * <p>
     * Both balances are read for the period asked for, so the totals, the running
     * balance column and the printed report are all answering the same question.
     */
    private void loadCard() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            AllAlerts.alertError(LanguageManager.getInstance().getString("item.card.date.required"));
            return;
        }
        if (from.isAfter(to)) {
            AllAlerts.alertError(LanguageManager.getInstance().getString("item.card.date.range.invalid"));
            return;
        }
        try {
            ProcessType selected = selectedProcessType();
            List<CardItems> loaded = new ArrayList<>(cardItemService.cardRows(numItem, from, to, selected));
            openingBalance = cardItemService.balanceBefore(numItem, from);
            closingBalance = cardItemService.balanceOn(numItem, to);
            // A running total over one kind of document is not a balance of anything -
            // the sales alone never put anything back on the shelf - so a card narrowed
            // to one kind has no balance column and no rows flagged by it.
            balanceShown = selected == null;
            if (balanceShown) ItemCardRunningBalance.apply(loaded, openingBalance);

            rows.setAll(loaded);
            totals = ItemCardTotals.of(loaded);
            loadedFrom = from;
            loadedTo = to;
            loadedProcessType = selected;
            showTotals();
            balanceColumn().ifPresent(column -> column.setVisible(balanceShown));
        } catch (Exception e) {
            logError(e);
        }
    }

    private Optional<TableColumn<CardItems, ?>> balanceColumn() {
        return tableView.getColumns().stream().filter(column -> "balance".equals(column.getId())).findFirst();
    }

    /** The kind of document the combo is narrowed to, or null for all four. */
    private ProcessType selectedProcessType() {
        int index = comboBox.getSelectionModel().getSelectedIndex();
        // Index 0 is "all"; the rest follow processTypeList, which is the enum in order.
        if (index <= 0) return null;
        return ProcessType.values()[index - 1];
    }

    private void showTotals() {
        textPurchase.setText(NUMBER.format(totals.purchase()));
        textSales.setText(NUMBER.format(totals.sales()));
        textRePurchase.setText(NUMBER.format(totals.purchaseReturn()));
        textReSales.setText(NUMBER.format(totals.salesReturn()));
        textCountTotals.setText(NUMBER.format(totals.netQuantity()));

        textCostPurchase.setText(NUMBER.format(totals.costPurchase()));
        textCostSales.setText(NUMBER.format(totals.costSales()));
        textCostSalesRe.setText(NUMBER.format(totals.costSalesReturn()));
        textCostPurchaseRe.setText(NUMBER.format(totals.costPurchaseReturn()));
        textCostTotals.setText(NUMBER.format(totals.profit()));

        textOpeningBalance.setText(NUMBER.format(openingBalance));
        textClosingBalance.setText(NUMBER.format(closingBalance));
    }

    /** Prints exactly what is on screen - the same period, the same rows, the same totals. */
    private void print() {
        if (loadedFrom == null || loadedTo == null) return;
        try {
            new Print_Reports().printCardItem(numItem,
                    totals.purchase(), totals.sales(), totals.purchaseReturn(), totals.salesReturn(),
                    openingBalance, closingBalance,
                    loadedFrom.toString(), loadedTo.toString(),
                    CardItemDao.tableNameOf(loadedProcessType));
        } catch (Exception e) {
            logError(e);
        }
    }

    /** Flags a movement that left the item at or below nothing on the shelf. */
    private void applyRowColoringForBalance() {
        tableView.setRowFactory(itemsModelTableView -> {
            TableRow<CardItems> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) ->
                    row.setStyle(newValue != null && balanceShown && newValue.getBalance() <= 0.0
                            ? "-fx-background-color: rgba(243,253,163,0.62)"
                            : ""));
            return row;
        });
    }

    private void addColumnShowInvoice() {
        tableView.getColumns().add(new ButtonColumn<>(new ButtonColumnI() {
            @Override
            public void action(int index) {
                try {
                    CardItems cardItems = tableView.getItems().get(index);
                    int id = cardItems.getInvoice_num();
                    String name = cardItems.getNameItem();
                    ProcessType processType = cardItems.getProcessType();
                    new ShowInvoiceApplication(dataPublisher, dataInterface(processType, daoFactory, dataPublisher), daoFactory, id, name);
                } catch (Exception e) {
                    logError(e);
                }
            }

            @NotNull
            @Override
            public String columnTitle() {
                return "";
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("show");
            }
        }));
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return LanguageManager.getInstance().getString("item.card.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void logError(Exception e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.card.title"), e);
    }
}
