package com.hamza.account.controller.target;

import com.hamza.account.features.chart.ChartDesign;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.model.PrintTotalsData;
import com.hamza.account.controller.model.TreeAccountModelForPrint;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.controller.reports.ToolbarReportsNameController;
import com.hamza.account.controller.reports.ToolbarReportsNameInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.interfaces.impl_dataInterface.CustomDataReturn;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.TargetsDetails;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.AccountDetailsApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.CheckComboBox;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hamza.controlsfx.others.MonthsWithArabic.retrieveArabicMonths;

@Log4j2
@FxmlPath(pathFile = "target/target-delegate.fxml")
public class TargetsController extends ServiceData implements AppSettingInterface {

    //TODO 11/11/2025 11:02 AM Mohamed: check all data
    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;
    private final ObservableList<XYChart.Series<String, Number>> seriesList = FXCollections.observableArrayList();
    private final ObservableList<TargetsDetails> filteredTargets = FXCollections.observableArrayList();
    private final List<TargetsDetails> targetsDetailsList = new ArrayList<>();
    private final ListProperty<Integer> month = new SimpleListProperty<>();
    private final IntegerProperty year = new SimpleIntegerProperty(1);
    private final Print_Reports printReports;
    private final String textName;
    @FXML
    private CheckComboBox<Integer> comboBoxMonth;
    @FXML
    private Label labelName, labelYear, labelMonth;
    @FXML
    private ComboBox<String> comboBoxName;
    @FXML
    private ComboBox<Integer> comboBoxYear;
    @FXML
    private TableView<TargetsDetails> tableView;
    @FXML
    private Button btnSearch;
    @FXML
    private ToolbarReportsNameController reportsToolbar;
    @FXML
    private VBox boxSales, boxSalesRe, boxAccount;
    private TableView<Total_Sales> tableTotalSales;
    private TableView<Total_Sales_Re> tableTotalSalesRe;
    private TableView<CustomerAccount> tableCustomerAccount;

    public TargetsController(DaoFactory daoFactory, DataPublisher dataPublisher, String textName) throws Exception {
        super(daoFactory);
        this.dataPublisher = dataPublisher;
        this.daoFactory = daoFactory;
        this.textName = textName;
        printReports = new Print_Reports();
    }


    @FXML
    public void initialize() {
        addTables();
        refreshData();
        otherSetting();
        addDataToTable();
        action();
        dataPublisher.getPublisherSales().addObserver(message -> refreshData());
        dataPublisher.getAfterAddTarget().addObserver(message -> refreshData());
    }

    private void addTables() {
        try {
            // add tables for total sales
            tableTotalSales();

            // add tables for total sales return
            tableTotalSalesRe();

            // add tables for account customer
            tableCustomerAccount();

        } catch (IOException e) {
            logErrors(e);
        }
    }

    private void tableTotalSales() throws IOException {
        OpenTargetTable<Total_Sales> targetTableController = new OpenTargetTable<>(new AddDataInterface<>() {
            @Override
            public ToDoubleFunction<Total_Sales> getColumnValuePaid() {
                return BaseTotals::getTotal_after_discount;
            }

            @Override
            public ToDoubleFunction<Total_Sales> getColumnValueAmount() {
                return BaseTotals::getRest;
            }

            @Override
            public void printInvoice(TableView<Total_Sales> tableView) {
                List<PrintTotalsData> printTotalsDataList = new ArrayList<>();
                List<Total_Sales> list = tableView.getItems();
                list.forEach(t2 -> printTotalsDataList.add(new PrintTotalsData(t2.getId(), t2.getCustomers().getName()
                        , t2.getDate(), t2.getInvoiceType().getType()
                        , t2.getTotal(), t2.getDiscount(), t2.getTotal_after_discount()
                        , t2.getPaid())));

                printReports.printTotalsInvoice(printTotalsDataList, "name", "", "", null);
            }
        });
        tableTotalSales = targetTableController.getTargetTableController().getTableView();
        new TableColumnAnnotation().getTable(tableTotalSales, BaseTotals.class, Total_Sales.class);
        targetTableController.getTargetTableController().setColumnIndex(5);
        targetTableController.getTargetTableController().setColumnIndex1(6);
        boxSales.getChildren().add(targetTableController.getPane());
        addColumnCustomerTotals(tableTotalSales);
    }

    private void tableTotalSalesRe() throws IOException {
        OpenTargetTable<Total_Sales_Re> targetTableController = new OpenTargetTable<>(new AddDataInterface<>() {
            @Override
            public ToDoubleFunction<Total_Sales_Re> getColumnValuePaid() {
                return BaseTotals::getPaid;
            }

            @Override
            public ToDoubleFunction<Total_Sales_Re> getColumnValueAmount() {
                return BaseTotals::getRest;
            }

            @Override
            public void printInvoice(TableView<Total_Sales_Re> tableView) {
                List<PrintTotalsData> printTotalsDataList = new ArrayList<>();
                List<Total_Sales_Re> list = tableView.getItems();
                list.forEach(t2 -> printTotalsDataList.add(new PrintTotalsData(t2.getId(), t2.getCustomer().getName()
                        , t2.getDate(), t2.getInvoiceType().getType()
                        , t2.getTotal(), t2.getDiscount(), t2.getTotal_after_discount()
                        , t2.getPaid())));

                printReports.printTotalsInvoice(printTotalsDataList, "name", "", "", null);
            }
        });
        tableTotalSalesRe = targetTableController.getTargetTableController().getTableView();
        new TableColumnAnnotation().getTable(tableTotalSalesRe, BaseTotals.class, Total_Sales_Re.class);
        targetTableController.getTargetTableController().setColumnIndex(5);
        targetTableController.getTargetTableController().setColumnIndex1(6);
        boxSalesRe.getChildren().add(targetTableController.getPane());
        addColumnCustomerTotalsReturn(tableTotalSalesRe);
    }

    private void tableCustomerAccount() throws IOException {
        OpenTargetTable<CustomerAccount> targetTableController = new OpenTargetTable<>(new AddDataInterface<>() {
            @Override
            public ToDoubleFunction<CustomerAccount> getColumnValuePaid() {
                return CustomerAccount::getPaid;
            }

            @Override
            public ToDoubleFunction<CustomerAccount> getColumnValueAmount() {
                return CustomerAccount::getAmount;
            }

            @Override
            public void printInvoice(TableView<CustomerAccount> tableView) {
                List<TreeAccountModelForPrint> accountModelForPrints = new ArrayList<>();
                List<CustomerAccount> list = tableView.getItems();
                list.forEach(t4 -> {
                    TreeAccountModelForPrint e = new TreeAccountModelForPrint();
                    e.setId(t4.getCustomers().getId());
                    e.setName(t4.getCustomers().getName());
                    e.setDate(t4.getDate());
                    e.setPurchase(t4.getPurchase());
                    e.setPaid(t4.getPaid());
                    e.setAmount(t4.getAmount());
                    e.setNotes(t4.getNotes());
                    accountModelForPrints.add(e);
                });
                printReports.printTotalsAccounts(accountModelForPrints, null);
            }
        });
        tableCustomerAccount = targetTableController.getTargetTableController().getTableView();
        new TableColumnAnnotation().getTable(tableCustomerAccount, BaseAccount.class, CustomerAccount.class);
        targetTableController.getTargetTableController().setColumnIndex(3);
//        targetTableController.getTargetTableController().setColumnIndex1(6);
        boxAccount.getChildren().add(targetTableController.getPane());
        addColumnCustomerAccount(tableCustomerAccount);
    }

    private <T extends BaseAccount> void addColumnCustomerAccount(TableView<T> tableView) {
        // add column code
        Callback<TableColumn.CellDataFeatures<CustomerAccount, String>, ObservableValue<String>> callback = f -> f.getValue().getCustomers().nameProperty();
        TableColumn<CustomerAccount, String> column = new TableColumn<>("الاسم");
        column.setCellValueFactory(callback);
        tableView.getColumns().addFirst((TableColumn<T, ?>) column);
    }

    private <T extends BaseTotals> void addColumnCustomerTotals(TableView<T> tableView) {
        // add column code
        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> callback = f -> f.getValue().getCustomers().nameProperty();
        TableColumn<Total_Sales, String> column = new TableColumn<>("الاسم");
        column.setCellValueFactory(callback);
        tableView.getColumns().addFirst((TableColumn<T, ?>) column);
    }

    private <T extends BaseTotals> void addColumnCustomerTotalsReturn(TableView<T> tableView) {
        // add column code
        Callback<TableColumn.CellDataFeatures<Total_Sales_Re, String>, ObservableValue<String>> callback = f -> f.getValue().getCustomer().nameProperty();
        TableColumn<Total_Sales_Re, String> column = new TableColumn<>("الاسم");
        column.setCellValueFactory(callback);
        tableView.getColumns().addFirst((TableColumn<T, ?>) column);
    }


    private void otherSetting() {
        labelName.setText(Setting_Language.NAME_DELEGATE);
        labelYear.setText(Setting_Language.WORD_YEAR);
        labelMonth.setText(Setting_Language.MONTHS);
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        comboBoxName.setPromptText(Setting_Language.NAME_DELEGATE);
        comboBoxYear.setPromptText(Setting_Language.WORD_YEAR);

    }

    private void addDataToTable() {
        new TableColumnAnnotation().getTable(tableView, TargetsDetails.class);
        filteredTargets.setAll(filterTargets(targetsDetailsList));
        tableView.setItems(filteredTargets);
//        new AddColumnRateController(tableView).addColumnRate();
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void comboBoxSetting() {
        comboBoxName.setItems(FXCollections.observableArrayList(targetsDetailsList.stream().map(TargetsDetails::getEmployee_name).distinct().toList()));
        comboBoxName.getItems().addFirst(Setting_Language.WORD_ALL);
        comboBoxName.getSelectionModel().select(0);

        comboBoxYear.setItems(FXCollections.observableArrayList(targetsDetailsList.stream().map(TargetsDetails::getSales_year).distinct().toList()));
        comboBoxYear.getSelectionModel().select((Integer) LocalDate.now().getYear());

        List<Integer> range = IntStream.rangeClosed(1, 12)
                .boxed().collect(Collectors.toList());
        comboBoxMonth.getItems().addAll(FXCollections.observableList(range));
        comboBoxMonth.setPrefWidth(150);
        comboBoxMonth.getCheckModel().check((Integer) LocalDate.now().getMonthValue());

        comboBoxMonth.getCheckModel().getCheckedItems().addListener((ListChangeListener<? super Integer>) change -> {
            if (change.next()) {
                month.set(FXCollections.observableArrayList(comboBoxMonth.getCheckModel().getCheckedItems()));
            }
        });

//        comboBoxYear.getSelectionModel().selectedItemProperty()
//                .addListener((observable, oldValue, newValue) -> year.set(newValue));
        year.bind(comboBoxYear.getSelectionModel().selectedItemProperty());
        month.set(FXCollections.observableArrayList(comboBoxMonth.getCheckModel().getCheckedItems()));
    }

    private void action() {
        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                addTableTotalsSalse(newValue.getEmployee_id());
//                addTableTotalsSalseReturn(newValue.getEmployee_id());
                addTableCustomerAccount(newValue.getEmployee_id());
            }
        });

        tableCustomerAccount.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                var selectedItem = tableCustomerAccount.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    try {
                        var customers = selectedItem.getCustomers();
                        var customData = new CustomData(daoFactory, dataPublisher);
                        new AccountDetailsApplication<>(daoFactory, dataPublisher, customData, customers.getName(), customers.getId());
                    } catch (Exception e) {
                        logErrors(e);
                    }
                }
            }
        });

        tableTotalSales.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                var selectedItem = tableTotalSales.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    try {
                        showInvoiceForSelectedItem(new CustomData(daoFactory, dataPublisher));
                    } catch (Exception e) {
                        logErrors(e);
                    }
                }
            }
        });

        tableTotalSalesRe.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                try {
                    showInvoiceForSelectedItem(new CustomDataReturn(daoFactory, dataPublisher));
                } catch (Exception e) {
                    logErrors(e);
                }
            }
        });

        btnSearch.setOnAction(event -> {
            filteredTargets.setAll(filterTargets(targetsDetailsList));
            seriesList.setAll(maxItems());
        });

        btnSearch.fire();

        ToolbarReportsNameInterface toolbarReportsNameInterface = new ToolbarReportsNameInterface() {
            @Override
            public String setTitle() {
                return Setting_Language.REPORT_DELEGATE;
            }

            @Override
            public void print() throws Exception {
                actionPrint();
            }

            @Override
            public void refresh() throws Exception {
                refreshData();
            }
        };
        reportsToolbar.setReportToolbar(toolbarReportsNameInterface);
    }

    private <T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
    void showInvoiceForSelectedItem(DataInterface<T1, T2, T3, T4> dataInterface) {
        var selectedItem = tableTotalSalesRe.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            try {
                var customers = selectedItem.getCustomer();
                new ShowInvoiceApplication<>(dataPublisher, dataInterface, daoFactory, selectedItem.getId(), customers.getName());
            } catch (Exception e) {
                logErrors(e);
            }
        }
    }

    private void actionPrint() {
        try {
            String selectedEmployee = comboBoxName.getSelectionModel().isEmpty() || comboBoxName.getSelectionModel().isSelected(0) ? null : comboBoxName.getSelectionModel().getSelectedItem();
            Integer selectedYear = comboBoxYear.getSelectionModel().isEmpty() ? null : comboBoxYear.getSelectionModel().getSelectedItem();
            List<Integer> selectedMonths = comboBoxMonth.checkModelProperty().getValue().isEmpty() ? null : comboBoxMonth.checkModelProperty().getValue().getCheckedItems().stream()
                    .filter(Objects::nonNull)
                    .toList();

            Integer firstMonth = null;
            Integer lastMonth = null;
            if (selectedMonths != null) {
                firstMonth = Collections.min(selectedMonths);
                lastMonth = Collections.max(selectedMonths);
            }


            printReports.printReportDelegate(selectedEmployee, selectedYear, firstMonth, lastMonth);
        } catch (DaoException e) {
            logErrors(e);
        }
    }

    private List<TargetsDetails> filterTargets(List<TargetsDetails> targetsDetailsList) {
        String selectedEmployee = comboBoxName.getSelectionModel().isEmpty() || comboBoxName.getSelectionModel().isSelected(0) ? null : comboBoxName.getSelectionModel().getSelectedItem();
        Integer selectedYear = comboBoxYear.getSelectionModel().isEmpty() ? null : comboBoxYear.getSelectionModel().getSelectedItem();
        List<Integer> selectedMonths = comboBoxMonth.checkModelProperty().getValue().isEmpty() ? null : comboBoxMonth.checkModelProperty().getValue().getCheckedItems();

        return targetsDetailsList.stream()
                .filter(targets -> selectedEmployee == null || targets.getEmployee_name().equals(selectedEmployee))
                .filter(targets -> selectedYear == null || targets.getSales_year() == selectedYear)
                .filter(targets -> selectedMonths == null || selectedMonths.contains(targets.getSales_month()))
                .toList();
    }

    private List<TargetsDetails> getTargetsList() {
        try {
            return targetDetailsService.getAllTargets();
        } catch (DaoException e) {
            logErrors(e);
        }
        return new ArrayList<>();
    }

    private void refreshData() {
        targetsDetailsList.clear();
        targetsDetailsList.addAll(getTargetsList());
        btnSearch.fire();
        comboBoxSetting();
    }

    private void addTableTotalsSalse(int delegateId) {
        var customerList = getTotalSales(delegateId)
                .stream()
                .filter(totalSales -> month.contains(LocalDate.parse(totalSales.getDate()).getMonthValue()) && year.get() == LocalDate.parse(totalSales.getDate()).getYear())
                .toList();
        ObservableList<Total_Sales> objects = FXCollections.observableArrayList(customerList);
        tableTotalSales.setItems(objects);
    }

    private void addTableTotalsSalseReturn(int delegateId) throws DaoException {
        var list = totalSalesReturnService.getListByCurrentMonth()
                .stream()
                .filter(totalSales -> totalSales.getEmployeeObject().getId() == delegateId)
                .filter(totalSales -> month.contains(LocalDate.parse(totalSales.getDate()).getMonthValue()) && year.get() == LocalDate.parse(totalSales.getDate()).getYear())
                .toList();
        ObservableList<Total_Sales_Re> objects = FXCollections.observableArrayList(list);
        tableTotalSalesRe.setItems(objects);
    }

    private void addTableCustomerAccount(int delegateId) {
        var totalSales = getTotalSales(delegateId)
                .stream()
                .map(totalSales1 -> totalSales1.getCustomers().getId()).toList();

        var customerList = accountCustomerService.accountTotalList(null, null)
                .stream()
                .filter(customerAccount -> totalSales.contains(customerAccount.getCustomers().getId()))
                .toList();
        ObservableList<CustomerAccount> objects = FXCollections.observableArrayList(customerList);
        tableCustomerAccount.setItems(objects);
    }

    private List<Total_Sales> getTotalSales(int delegateId) {
        List<Total_Sales> listByCurrentMonth = new ArrayList<>();
        try {
            listByCurrentMonth = totalSalesService.getListByCurrentMonth();
        } catch (DaoException e) {
            logErrors(e);
        }

        return listByCurrentMonth
                .stream()
                .filter(totalSales -> totalSales.getEmployeeObject().getId() == delegateId).toList();
    }

    private void addChart() {
        seriesList.setAll(maxItems());
        ChartDesign chartSales = new ChartDesign(seriesList
                , Setting_Language.MONTHS, Setting_Language.WORD_TOTAL
                , Setting_Language.TOTAL_SALES
                , LineChart::new);
//        pane.getChildren().add(chartSales.getPane());
        VBox.setVgrow(chartSales.getPane(), Priority.SOMETIMES);
    }

    private ObservableList<XYChart.Series<String, Number>> maxItems() {
        ObservableList<XYChart.Series<String, Number>> list = FXCollections.observableArrayList();
        for (TargetsDetails targetsDetails : filteredTargets) {
            List<String> arabicMonths = retrieveArabicMonths();
            XYChart.Series<String, Number> series5 = new XYChart.Series<>();
            series5.setName(targetsDetails.getEmployee_name());
            for (int i = 0; i < arabicMonths.size(); i++) {
                double target = 0;
                if (targetsDetails.getSales_month() == i + 1)
                    target = targetsDetails.getTotals_amount();

                series5.getData().add(new XYChart.Data<>(arabicMonths.get(i), target));
            }
            list.add(series5);
        }
        return list;
    }

    @Override
    public @NotNull Pane pane() throws IOException {
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

    private void logErrors(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
    }
}

@Getter
class OpenTargetTable<T> {
    private final Pane pane;
    private final TargetTableController<T> targetTableController;

    public OpenTargetTable(AddDataInterface<T> addDataInterface) throws IOException {
        targetTableController = new TargetTableController<>(addDataInterface);
        pane = new OpenFxmlApplication(targetTableController).getPane();
    }
}