package com.hamza.account.controller.reports;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "reports/summary.fxml")
public class SummaryController extends SummaryData implements AppSettingInterface {

    private final String date;
    private final String textName;
    private double netSales, netPurchase;
    @FXML
    private Label labelDay, labelPreviousDay, labelWeek, labelMonth;
    @FXML
    private Label amount;
    @FXML
    private Label cash1, cash2, cash3, cash4, cash5; // for cash
    @FXML
    private Label countPurchase, countPurchaseRe, countSales, countSalesRe; // for count
    @FXML
    private Label totalPurchase, totalPurchaseCash, totalPurchaseDefer, totalPurchaseDiscount, totalPurchaseRe; //for purchase
    @FXML
    private Label totalSales, totalSalesCash, totalSalesDefer, totalSalesDiscount, totalSalesRe; // for sales
    @FXML
    private Label totalPurchaseReDiscount, totalSalesReDiscount;
    @FXML
    private TableView<ItemsModel> tableView;
    @FXML
    private StackPane stackPane;

    public SummaryController(DaoFactory daoFactory, String textName) throws Exception {
        super(daoFactory);
        this.textName = textName;
        date = LocalDate.now().toString();
    }

    @FXML
    public void initialize() {
        getTableItems();
        bindData();
        MaskerPaneSetting maskerPaneSetting = new MaskerPaneSetting(stackPane);
        maskerPaneSetting.showMaskerPane(() -> tableView.setItems(FXCollections.observableArrayList(getCollection())));
        sumTotals();
        sumPurchase();
        sumSalesCurrentMonth();
        purchaseRe();
        salesRe();
        getCash();
        setAmountValue(netSales - netPurchase);

    }

    private List<ItemsModel> getCollection() {
        try {
            return itemsService.maxItemsSold();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
        }
        return List.of();
    }

    private void colorData() {
        PseudoClass empty = PseudoClass.getPseudoClass("empty");
//        pseudoClassStateChanged(empty,isNextToEmpty(sampleButton));
//
//        BooleanProperty myPseudoClassState = new BooleanPropertyBase(false) {
//
//            @Override public void invalidated() {
//                pseudoClassStateChanged(empty, get());
//            }
//
//            @Override public Object getBean() {
//                return MyControl.this;
//            }
//
//            @Override public String getName() {
//                return "myPseudoClassState";
//            }
//        };
    }

    private void bindData() {
        labelDay.textProperty().bind(labelDayValueProperty().asString());
        labelPreviousDay.textProperty().bind(labelPreviousDayValueProperty().asString());
        labelWeek.textProperty().bind(labelWeekValueProperty().asString());
        labelMonth.textProperty().bind(labelMonthValueProperty().asString());
        totalPurchase.textProperty().bind(totalPurchaseValueProperty().asString());
        totalPurchaseCash.textProperty().bind(totalPurchaseCashValueProperty().asString());
        totalPurchaseDefer.textProperty().bind(totalPurchaseDeferValueProperty().asString());
        totalPurchaseDiscount.textProperty().bind(totalPurchaseDiscountValueProperty().asString());
        totalPurchaseRe.textProperty().bind(totalPurchaseReValueProperty().asString());
        totalSales.textProperty().bind(totalSalesValueProperty().asString());
        totalSalesCash.textProperty().bind(totalSalesCashValueProperty().asString());
        totalSalesDefer.textProperty().bind(totalSalesDeferValueProperty().asString());
        totalSalesDiscount.textProperty().bind(totalSalesDiscountValueProperty().asString());
        totalSalesRe.textProperty().bind(totalSalesReValueProperty().asString());
        countPurchase.textProperty().bind(countPurchaseValueProperty().asString());
        countPurchaseRe.textProperty().bind(countPurchaseReValueProperty().asString());
        countSales.textProperty().bind(countSalesValueProperty().asString());
        countSalesRe.textProperty().bind(countSalesReValueProperty().asString());
        cash1.textProperty().bind(cash1ValueProperty().asString());
        cash2.textProperty().bind(cash2ValueProperty().asString());
        cash3.textProperty().bind(cash3ValueProperty().asString());
        cash4.textProperty().bind(cash4ValueProperty().asString());
        cash5.textProperty().bind(cash5ValueProperty().asString());
        amount.textProperty().bind(amountValueProperty().asString());
        totalPurchaseReDiscount.textProperty().bind(totalPurchaseReDiscountValueProperty().asString());
        totalSalesReDiscount.textProperty().bind(totalSalesReDiscountValueProperty().asString());

    }

    /**
     * Updates the label components to display the total sales figures.
     * The totals are calculated for various time periods, including today, the previous day,
     * the current week, and the current month. The values are retrieved from the totalSalesService.
     */
    private void sumTotals() {
        try {
            setLabelDayValue(totalSalesService.sumTotalByDay());
            setLabelPreviousDayValue(totalSalesService.sumPreviousDay());
            setLabelWeekValue(totalSalesService.sumWeek());
            setLabelMonthValue(totalSalesService.sumMonth());
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * Summarizes the purchase data for a specific date and updates relevant UI elements.
     * This method performs the following actions:
     * - Retrieves a list of total purchases for the specified date.
     * - Calculates the sum of total amounts, discounts, and deferred payments from the list.
     * - Updates UI labels with total purchases, cash purchases, deferred purchases, discounts, and purchase count.
     * - Calculates and stores the net purchase value by deducting the sum of discounts and deferred payments from the total amount.
     */
    private void sumPurchase() {
        try {
            List<Total_buy> totalBuys = totalBuyService.totalBuyByDate(date);

            // add purchase lists
//        totalBuys.forEach(totalBuy -> totalBuy.setPurchaseList(purchaseService.getPurchaseById(totalBuy.getId())));

            // get sum discount for items from purchase
            double sumDiscountForItemInPurchase = totalBuys.stream().mapToDouble(value -> value.getPurchaseList().stream().mapToDouble(Purchase::getDiscount).sum()).sum();
            double sumTotal = totalBuys.stream().mapToDouble(Total_buy::getTotal).sum();
            double sumDiscount = totalBuys.stream().mapToDouble(Total_buy::getDiscount).sum();
            double sumDefer = totalBuys.stream().filter(totalBuy -> totalBuy.getInvoiceType() == InvoiceType.DEFER).mapToDouble(Total_buy::getRest).sum();

            setTotalPurchaseValue(sumTotal);
            setTotalPurchaseCashValue(sumTotal - sumDefer);
            setTotalPurchaseDeferValue(sumDefer);
            setTotalPurchaseDiscountValue(sumDiscount + sumDiscountForItemInPurchase);
            setCountPurchaseValue(totalBuys.size());
            netPurchase = sumTotal - sumDiscount - sumDefer;
        } catch (DaoException e) {
            log.error(e.getMessage());
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * Calculates and updates the summary statistics of sales such as total sales, total cash sales,
     * deferred sales, discounts, and net sales. It also sets these values to the respective UI components.
     * <p>
     * This method performs the following operations:
     * - Filters the sales for a specific date from the total sales list.
     * - Sums up the total sales, discounts, and deferred amounts.
     * - Updates the UI components with these calculated values including:
     * - Total sales
     * - Total cash sales (total sales minus deferred sales)
     * - Total deferred sales
     * - Total discount
     * - Count of total sales
     * - Updates the net sales (total sales minus deferred sales and discounts).
     */
    private void sumSalesCurrentMonth() {
        try {
            List<Total_Sales> totalSalesList = totalSalesService.getListByCurrentMonth()
                    .stream().filter(totalSales1 -> totalSales1.getDate().equals(date)).toList();

            //TODO 11/11/2025 5:24 AM Mohamed: check this again and add sales list
            // add sales lists
//        totalSalesList.forEach(totalSales -> totalSales.setSalesList(salesService.fetchSalesByInvoiceNumber(totalSales.getId())));

            // get sum discount for items from sales
            double sumDiscountForItemInSales = totalSalesList.stream().mapToDouble(value -> value.getSalesList().stream().mapToDouble(Sales::getDiscount).sum()).sum();
            double sumTotal = totalSalesList.stream().mapToDouble(Total_Sales::getTotal).sum();
            double sumDiscount = totalSalesList.stream().mapToDouble(Total_Sales::getDiscount).sum();
            double sumDefer = totalSalesList.stream().filter(totalBuy -> totalBuy.getInvoiceType() == InvoiceType.DEFER).mapToDouble(Total_Sales::getRest).sum();

            setTotalSalesValue(sumTotal);
            setTotalSalesCashValue(sumTotal - sumDefer);
            setTotalSalesDeferValue(sumDefer);
            setTotalSalesDiscountValue(sumDiscount + sumDiscountForItemInSales);
            setCountSalesValue(totalSalesList.size());
            netSales = sumTotal - sumDefer - sumDiscount;
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * The purchaseRe method calculates and updates the total returned purchases for a specific date.
     * <p>
     * It queries the totalBuyReturnService to retrieve a list of Total_Buy_Re objects for the given 'date',
     * calculates the sum of 'total' fields from these objects, and updates the relevant UI labels
     * for the total sum and count of purchase returns. Additionally, it adjusts the netPurchase value
     * by subtracting the calculated sum of purchase returns.
     */
    private void purchaseRe() {
        try {
            List<Total_Buy_Re> totalBuyRes = totalBuyReturnService.getTotalBuyByDate(date);
            double sumTotalRe = totalBuyRes.stream().mapToDouble(Total_Buy_Re::getTotal).sum();
            double sumDiscount = totalBuyRes.stream().mapToDouble(Total_Buy_Re::getDiscount).sum();
//        totalBuyRes.forEach(totalBuyRe -> {
//            List<Purchase_Return> purchaseReturnsById = purchaseReService.getPurchaseReturnsById(Math.toIntExact(totalBuyRe.getId()));
//            totalBuyRe.setPurchaseReturnList(purchaseReturnsById);
//        });

            double sumDiscountForItemInSales = totalBuyRes.stream().mapToDouble(value -> value.getPurchaseReturnList().stream().mapToDouble(Purchase_Return::getDiscount).sum()).sum();
            setTotalPurchaseReValue(sumTotalRe);
            setCountPurchaseReValue(totalBuyRes.size());
            setTotalPurchaseReDiscountValue(sumDiscount + sumDiscountForItemInSales);
            netPurchase = netPurchase - sumTotalRe;
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * Updates the sales return information on the UI and recalculates the net sales.
     * <p>
     * This method filters the list of total sales returns to include only those for the current date.
     * It then sums up the total sales returns amount and updates the corresponding UI labels.
     * Finally, it adjusts the net sales by subtracting the total sales returns sum.
     */
    private void salesRe() {
        try {
            List<Total_Sales_Re> totalSalesReList = totalSalesReturnService.getListByCurrentMonth().stream()
                    .filter(totalSalesRe1 -> totalSalesRe1.getDate().equals(date))
                    .toList();
            double sumTotalSalesReturn = totalSalesReList.stream().mapToDouble(Total_Sales_Re::getTotal).sum();

//        totalSalesReList.forEach(totalBuyRe -> {
//            List<Sales_Return> purchaseReturnsById = salesReService.getSalesByInvoiceNumber(Math.toIntExact(totalBuyRe.getId()));
//            totalBuyRe.setSalesReturnList(purchaseReturnsById);
//        });

            double sumDiscountForItemInSales = totalSalesReList.stream()
                    .mapToDouble(value -> value.getSalesReturnList().stream().mapToDouble(Sales_Return::getDiscount).sum()).sum();
            double sumDiscount = totalSalesReList.stream().mapToDouble(Total_Sales_Re::getDiscount).sum();
            setTotalSalesReValue(sumTotalSalesReturn);
            setCountSalesReValue(totalSalesReList.size());
            setTotalSalesReDiscountValue(sumDiscount + sumDiscountForItemInSales);
            netSales = netSales - sumTotalSalesReturn;
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    /**
     * Configures and populates the tableView with data specific to the ItemsModel.
     * It initially hides all columns except the one at index 2.
     * Then, it adds an additional column configured to display sum of sales for each item.
     */
    private void getTableItems() {
        new TableColumnAnnotation().getTable(tableView, ItemsModel.class);
        for (int i = 0; i < tableView.getColumns().size(); i++) {
            if (i != 2)
                tableView.getColumns().get(i).setVisible(false);
        }
        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnSales = f -> f.getValue().sumSalesProperty().asObject();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_SALES, 3, columnSales);
    }

    /**
     * Retrieves the list of all expense details and updates the cash3 field
     * to display the total amount of all expenses.
     * <p>
     * This method fetches all expenses details using the expensesDetailsService,
     * calculates the sum of amounts for these expenses, and sets this sum
     * to the text property of the cash3 field.
     */
    private void getCash() {
        try {
            List<ExpensesDetails> expenses = expensesDetailsService.fetchAllExpensesDetailsList();
            setCash3Value(expenses.stream().mapToDouble(ExpensesDetails::getAmount).sum());
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
        }
    }

    @Override
    public Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return textName;
    }

    @Override
    public boolean resize() {
        return true;
    }
}


class SummaryData extends ServiceData {

    private final DoubleProperty labelDayValue = new SimpleDoubleProperty(0);
    private final DoubleProperty labelPreviousDayValue = new SimpleDoubleProperty(0);
    private final DoubleProperty labelWeekValue = new SimpleDoubleProperty(0);
    private final DoubleProperty labelMonthValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseCashValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseDeferValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseDiscountValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseReValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalPurchaseReDiscountValue = new SimpleDoubleProperty(0);

    private final DoubleProperty totalSalesValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalSalesCashValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalSalesDeferValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalSalesDiscountValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalSalesReValue = new SimpleDoubleProperty(0);
    private final DoubleProperty totalSalesReDiscountValue = new SimpleDoubleProperty(0);

    private final DoubleProperty countPurchaseValue = new SimpleDoubleProperty(0);
    private final DoubleProperty countPurchaseReValue = new SimpleDoubleProperty(0);
    private final DoubleProperty countSalesValue = new SimpleDoubleProperty(0);
    private final DoubleProperty countSalesReValue = new SimpleDoubleProperty(0);

    private final DoubleProperty cash1Value = new SimpleDoubleProperty(0);
    private final DoubleProperty cash2Value = new SimpleDoubleProperty(0);
    private final DoubleProperty cash3Value = new SimpleDoubleProperty(0);
    private final DoubleProperty cash4Value = new SimpleDoubleProperty(0);
    private final DoubleProperty cash5Value = new SimpleDoubleProperty(0);
    private final DoubleProperty amountValue = new SimpleDoubleProperty(0);

    public SummaryData(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    public DoubleProperty labelDayValueProperty() {
        return labelDayValue;
    }

    public double getLabelPreviousDayValue() {
        return labelPreviousDayValue.get();
    }

    public void setLabelPreviousDayValue(double labelPreviousDayValue) {
        this.labelPreviousDayValue.set(roundToTwoDecimalPlaces(labelPreviousDayValue));
    }

    public DoubleProperty labelPreviousDayValueProperty() {
        return labelPreviousDayValue;
    }

    public double getLabelDayValue() {
        return labelDayValue.get();
    }

    public void setLabelDayValue(double labelDayValue) {
        this.labelDayValue.set(roundToTwoDecimalPlaces(labelDayValue));
    }

    public double getLabelWeekValue() {
        return labelWeekValue.get();
    }

    public void setLabelWeekValue(double labelWeekValue) {
        this.labelWeekValue.set(roundToTwoDecimalPlaces(labelWeekValue));
    }

    public DoubleProperty labelWeekValueProperty() {
        return labelWeekValue;
    }

    public double getLabelMonthValue() {
        return labelMonthValue.get();
    }

    public void setLabelMonthValue(double labelMonthValue) {
        this.labelMonthValue.set(roundToTwoDecimalPlaces(labelMonthValue));
    }

    public DoubleProperty labelMonthValueProperty() {
        return labelMonthValue;
    }

    public double getTotalPurchaseValue() {
        return totalPurchaseValue.get();
    }

    public void setTotalPurchaseValue(double totalPurchaseValue) {
        this.totalPurchaseValue.set(roundToTwoDecimalPlaces(totalPurchaseValue));
    }

    public DoubleProperty totalPurchaseValueProperty() {
        return totalPurchaseValue;
    }

    public double getTotalPurchaseCashValue() {
        return totalPurchaseCashValue.get();
    }

    public void setTotalPurchaseCashValue(double totalPurchaseCashValue) {
        this.totalPurchaseCashValue.set(roundToTwoDecimalPlaces(totalPurchaseCashValue));
    }

    public DoubleProperty totalPurchaseCashValueProperty() {
        return totalPurchaseCashValue;
    }

    public double getTotalPurchaseDeferValue() {
        return totalPurchaseDeferValue.get();
    }

    public void setTotalPurchaseDeferValue(double totalPurchaseDeferValue) {
        this.totalPurchaseDeferValue.set(roundToTwoDecimalPlaces(totalPurchaseDeferValue));
    }

    public DoubleProperty totalPurchaseDeferValueProperty() {
        return totalPurchaseDeferValue;
    }

    public double getTotalPurchaseDiscountValue() {
        return totalPurchaseDiscountValue.get();
    }

    public void setTotalPurchaseDiscountValue(double totalPurchaseDiscountValue) {
        this.totalPurchaseDiscountValue.set(roundToTwoDecimalPlaces(totalPurchaseDiscountValue));
    }

    public DoubleProperty totalPurchaseDiscountValueProperty() {
        return totalPurchaseDiscountValue;
    }

    public double getTotalPurchaseReValue() {
        return totalPurchaseReValue.get();
    }

    public void setTotalPurchaseReValue(double totalPurchaseReValue) {
        this.totalPurchaseReValue.set(roundToTwoDecimalPlaces(totalPurchaseReValue));
    }

    public DoubleProperty totalPurchaseReValueProperty() {
        return totalPurchaseReValue;
    }

    public double getTotalSalesValue() {
        return totalSalesValue.get();
    }

    public void setTotalSalesValue(double totalSalesValue) {
        this.totalSalesValue.set(roundToTwoDecimalPlaces(totalSalesValue));
    }

    public DoubleProperty totalSalesValueProperty() {
        return totalSalesValue;
    }

    public double getTotalSalesCashValue() {
        return totalSalesCashValue.get();
    }

    public void setTotalSalesCashValue(double totalSalesCashValue) {
        this.totalSalesCashValue.set(roundToTwoDecimalPlaces(totalSalesCashValue));
    }

    public DoubleProperty totalSalesCashValueProperty() {
        return totalSalesCashValue;
    }

    public double getTotalSalesDeferValue() {
        return totalSalesDeferValue.get();
    }

    public void setTotalSalesDeferValue(double totalSalesDeferValue) {
        this.totalSalesDeferValue.set(roundToTwoDecimalPlaces(totalSalesDeferValue));
    }

    public DoubleProperty totalSalesDeferValueProperty() {
        return totalSalesDeferValue;
    }

    public double getTotalSalesDiscountValue() {
        return totalSalesDiscountValue.get();
    }

    public void setTotalSalesDiscountValue(double totalSalesDiscountValue) {
        this.totalSalesDiscountValue.set(roundToTwoDecimalPlaces(totalSalesDiscountValue));
    }

    public DoubleProperty totalSalesDiscountValueProperty() {
        return totalSalesDiscountValue;
    }

    public double getTotalSalesReValue() {
        return totalSalesReValue.get();
    }

    public void setTotalSalesReValue(double totalSalesReValue) {
        this.totalSalesReValue.set(roundToTwoDecimalPlaces(totalSalesReValue));
    }

    public DoubleProperty totalSalesReValueProperty() {
        return totalSalesReValue;
    }

    public double getCountPurchaseValue() {
        return countPurchaseValue.get();
    }

    public void setCountPurchaseValue(double countPurchaseValue) {
        this.countPurchaseValue.set(roundToTwoDecimalPlaces(countPurchaseValue));
    }

    public DoubleProperty countPurchaseValueProperty() {
        return countPurchaseValue;
    }

    public double getCountPurchaseReValue() {
        return countPurchaseReValue.get();
    }

    public void setCountPurchaseReValue(double countPurchaseReValue) {
        this.countPurchaseReValue.set(roundToTwoDecimalPlaces(countPurchaseReValue));
    }

    public DoubleProperty countPurchaseReValueProperty() {
        return countPurchaseReValue;
    }

    public double getCountSalesValue() {
        return countSalesValue.get();
    }

    public void setCountSalesValue(double countSalesValue) {
        this.countSalesValue.set(roundToTwoDecimalPlaces(countSalesValue));
    }

    public DoubleProperty countSalesValueProperty() {
        return countSalesValue;
    }

    public double getCountSalesReValue() {
        return countSalesReValue.get();
    }

    public void setCountSalesReValue(double countSalesReValue) {
        this.countSalesReValue.set(roundToTwoDecimalPlaces(countSalesReValue));
    }

    public DoubleProperty countSalesReValueProperty() {
        return countSalesReValue;
    }

    public double getCash1Value() {
        return cash1Value.get();
    }

    public void setCash1Value(double cash1Value) {
        this.cash1Value.set(roundToTwoDecimalPlaces(cash1Value));
    }

    public DoubleProperty cash1ValueProperty() {
        return cash1Value;
    }

    public double getCash2Value() {
        return cash2Value.get();
    }

    public void setCash2Value(double cash2Value) {
        this.cash2Value.set(roundToTwoDecimalPlaces(cash2Value));
    }

    public DoubleProperty cash2ValueProperty() {
        return cash2Value;
    }

    public double getCash3Value() {
        return cash3Value.get();
    }

    public void setCash3Value(double cash3Value) {
        this.cash3Value.set(roundToTwoDecimalPlaces(cash3Value));
    }

    public DoubleProperty cash3ValueProperty() {
        return cash3Value;
    }

    public double getCash4Value() {
        return cash4Value.get();
    }

    public void setCash4Value(double cash4Value) {
        this.cash4Value.set(roundToTwoDecimalPlaces(cash4Value));
    }

    public DoubleProperty cash4ValueProperty() {
        return cash4Value;
    }

    public double getCash5Value() {
        return cash5Value.get();
    }

    public void setCash5Value(double cash5Value) {
        this.cash5Value.set(roundToTwoDecimalPlaces(cash5Value));
    }

    public DoubleProperty cash5ValueProperty() {
        return cash5Value;
    }

    public double getAmountValue() {
        return amountValue.get();
    }

    public void setAmountValue(double amountValue) {
        this.amountValue.set(roundToTwoDecimalPlaces(amountValue));
    }

    public DoubleProperty amountValueProperty() {
        return amountValue;
    }

    public double getTotalPurchaseReDiscountValue() {
        return totalPurchaseReDiscountValue.get();
    }

    public void setTotalPurchaseReDiscountValue(double totalPurchaseReDiscountValue) {
        this.totalPurchaseReDiscountValue.set(roundToTwoDecimalPlaces(totalPurchaseReDiscountValue));
    }

    public DoubleProperty totalPurchaseReDiscountValueProperty() {
        return totalPurchaseReDiscountValue;
    }

    public double getTotalSalesReDiscountValue() {
        return totalSalesReDiscountValue.get();
    }

    public void setTotalSalesReDiscountValue(double totalSalesReDiscountValue) {
        this.totalSalesReDiscountValue.set(roundToTwoDecimalPlaces(totalSalesReDiscountValue));
    }

    public DoubleProperty totalSalesReDiscountValueProperty() {
        return totalSalesReDiscountValue;
    }
}
