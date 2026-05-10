package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.TreasuryBalanceService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.service.UsersService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.controller.MaskerPaneSetting;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.util.NumberUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

import static com.hamza.account.controller.items.CardController.dataInterface;

@Log4j2
@FxmlPath(pathFile = "treasury/treasury-details.fxml")
public class TreasureDetailsController {

    public static final String SALES_TITLE = "المبيعات";
    public static final String RETURNED_SALES_TITLE = "مرتجع المبيعات";
    public static final String PURCHASES_TITLE = "المشتريات";
    public static final String RETURNED_PURCHASES_TITLE = "مرتجع المشتريات";
    public static final String CUSTOMER_ACCOUNTS_TITLE = "حسابات العملاء";
    public static final String SUPPLIER_ACCOUNT_TITLE = "حسابات الموردين";
    public static final String EXPENSES_TITLE = "المصروفات";
    private final ObservableList<TreasuryBalance> treasuryBalances = FXCollections.observableArrayList();
    //    private final OpenTimeSearchApplication openTimeSearchApplication;
    private final LongProperty countInvoiceSales = new SimpleLongProperty(0);
    private final LongProperty countInvoicePurchase = new SimpleLongProperty(0);
    private final DoubleProperty sumSales = new SimpleDoubleProperty();
    private final DoubleProperty sumPurchases = new SimpleDoubleProperty();
    private final DoubleProperty sumSalesRe = new SimpleDoubleProperty();
    private final DoubleProperty sumPurchaseRe = new SimpleDoubleProperty();
    private final DoubleProperty sumCustomerPaid = new SimpleDoubleProperty();
    private final DoubleProperty sumSuppPaid = new SimpleDoubleProperty();
    private final DoubleProperty sumExpenses = new SimpleDoubleProperty();
    private final DoubleProperty sumIncomeIn = new SimpleDoubleProperty();
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final UsersService userService = ServiceRegistry.get(UsersService.class);
    private final TreasuryBalanceService treasuryBalanceService = ServiceRegistry.get(TreasuryBalanceService.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    @FXML
    private ComboBox<String> comboTreasury, comboDetails, comboUsers;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Button btnRefresh, btnSearch, btnPrint, btnPrintSummary;
    @FXML
    private Text sumIncome, sumOutput, sumBalance;
    @FXML
    private TableView<TreasuryBalance> tableView;
    @FXML
    private StackPane stackPane;
    @FXML
    private MaskerPaneSetting maskerPaneSetting;
    @FXML
    private CheckBox checkTime;
    private FilteredList<TreasuryBalance> filteredList;

    public TreasureDetailsController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
//        dateFrom.setValue(firstDateInMonth);
        buttonGraphics();
        selectNextId();
        addComboTreasury();
        getTable();
        otherSetting();
        btnSearch.fire();
        updateFinancialSummaries();
    }

    private void selectNextId() {
        // select user
        if (LogApplication.usersVo.getId() != 1) {
            try {
                var usersById = userService.getUsersById(LogApplication.usersVo.getId());
                comboUsers.setDisable(true);
                comboUsers.getSelectionModel().select(usersById.getUsername());
                comboTreasury.setDisable(true);
                comboDetails.setDisable(true);
                dateFrom.setDisable(true);
                dateTo.setDisable(true);
                dateFrom.setValue(LocalDate.now());
                filteredList.setPredicate(filterByDate().and(filterByDetails()).and(filterByUsers()).and(filterByTime()));
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        }
    }

    private void buttonGraphics() {
        var image = new Image_Setting();
        btnRefresh.setGraphic(ImageChoose.createIcon(image.refresh));
        btnPrint.setGraphic(ImageChoose.createIcon(image.print));
        btnSearch.setGraphic(ImageChoose.createIcon(image.search));
        btnPrintSummary.setGraphic(ImageChoose.createIcon(image.show));
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, TreasuryBalance.class);
        filteredList = new FilteredList<>(treasuryBalances);
        SortedList<TreasuryBalance> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
        TableSetting.tableMenuSetting(getClass(), tableView);
        addTableColumns();
    }

    private void addTableColumns() {
        TableColumn<TreasuryBalance, String> printColumn = new TableColumn<>(Setting_Language.WORD_SHOW);
        printColumn.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button(Setting_Language.WORD_SHOW);

            {

                btn.setOnAction(e -> {
                    try {
                        TreasuryBalance balance = getTableView().getItems().get(getIndex());
                        // Print logic here
                        ProcessType processTypeSales = ProcessType.SALES;
                        ProcessType processTypeSalesRe = ProcessType.SALES_RETURN;
                        ProcessType processTypePurchase = ProcessType.PURCHASE;
                        ProcessType processTypePurchaseRe = ProcessType.PURCHASE_RETURN;
                        int id = balance.getId();
                        if (balance.getInformation().equals(processTypeSales.getType()) || balance.getInformation().equals(processTypeSalesRe.getType())
                                || balance.getInformation().equals(processTypePurchase.getType()) || balance.getInformation().equals(processTypePurchaseRe.getType())) {
                            ProcessType processType;
                            if (balance.getInformation().equals(processTypeSales.getType()))
                                processType = processTypeSales;
                            else if (balance.getInformation().equals(processTypeSalesRe.getType()))
                                processType = processTypeSalesRe;
                            else if (balance.getInformation().equals(processTypePurchase.getType()))
                                processType = processTypePurchase;
                            else processType = processTypePurchaseRe;

                            new ShowInvoiceApplication<>(dataPublisher, dataInterface(processType, daoFactory, dataPublisher), daoFactory, id, "");
                        }
                    } catch (Exception ex) {
                        log.error(ex.getMessage(), ex.getCause());
                    }

                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableView.getColumns().add(printColumn);
    }


    private void addComboTreasury() {
        comboTreasury.getItems().clear();
        comboTreasury.getItems().addAll(getListTreasuryModelNames());
        comboTreasury.getSelectionModel().selectFirst();

        String[] strings = {"الكل", PURCHASES_TITLE, RETURNED_PURCHASES_TITLE, SALES_TITLE, RETURNED_SALES_TITLE, CUSTOMER_ACCOUNTS_TITLE, SUPPLIER_ACCOUNT_TITLE, EXPENSES_TITLE, "إيداع", "صرف"};
        comboDetails.getItems().clear();
        comboDetails.getItems().addAll(strings);
        comboDetails.getSelectionModel().selectFirst();

        comboUsers.getItems().clear();
        comboUsers.getItems().add(Setting_Language.WORD_ALL);
        comboUsers.getItems().addAll(getUsersNames());
        comboUsers.getSelectionModel().selectFirst();
    }

    @NotNull
    private List<String> getListTreasuryModelNames() {
        try {
            return treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    private List<String> getUsersNames() {
        try {
            return userService.getUsersNames();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    private void otherSetting() {
        btnSearch.setOnAction(actionEvent -> {
            refreshTableView();
            filteredList.setPredicate(filterByDetails().and(filterByUsers()).and(filterByTime()));
        });

        var printReports = new Print_Reports();
        btnPrint.setOnAction(actionEvent -> printReports.printAccountStatements(tableView.getItems(), dateFrom.getValue().toString()
                , dateTo.getValue().toString()
                , Double.parseDouble(sumIncome.getText()), Double.parseDouble(sumOutput.getText()), Double.parseDouble(sumBalance.getText())));

        btnPrintSummary.setOnAction(actionEvent -> printReports.printSummary(LocalDate.now().toString()
                , comboUsers.getSelectionModel().getSelectedItem(), dateFrom.getValue().toString(), dateTo.getValue().toString()
                , countInvoiceSales.get(), sumSales.get(), sumCustomerPaid.get(), sumSalesRe.get(), sumExpenses.get()
                , countInvoicePurchase.get(), sumPurchases.get(), sumSuppPaid.get(), sumPurchaseRe.get(), sumIncomeIn.get()));

//        btnPrintSummary.disableProperty().bind(comboUsers.getSelectionModel().selectedItemProperty().isEqualTo(Setting_Language.WORD_ALL));

        // filter data
        comboUsers.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> filteredList.setPredicate(filterByDate().and(filterByDetails()).and(filterByUsers()).and(filterByTime())));
        comboDetails.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> filteredList.setPredicate(filterByDate().and(filterByDetails()).and(filterByUsers()).and(filterByTime())));

    }

    private void refreshTableView() {
        maskerPaneSetting.showMaskerPane(() -> {
            try {
                var treasuryBalanceSummary = treasuryBalanceService.getAllTreasuryBalanceBetweenTwoDate(dateFrom.getValue().toString()
                                , dateTo.getValue().toString())
                        .stream()
//                        .filter(treasuryBalance -> treasuryBalance.getTotal_income() != 0 && treasuryBalance.getTotal_output() != 0)
                        .toList();
                treasuryBalances.clear();
                treasuryBalances.addAll(treasuryBalanceSummary);
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
            }
        });

        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> updateFinancialSummaries());
    }

    private void sumData() {
        var items = tableView.getItems().stream().filter(filterByUsers()).toList();
        var filteredSalesData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(SALES_TITLE));
        var filteredSalesReData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(RETURNED_SALES_TITLE));
        var filteredPurchaseData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(PURCHASES_TITLE));
        var filteredPurchaseReData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(RETURNED_PURCHASES_TITLE));
        var filteredCustomerAccountsData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(CUSTOMER_ACCOUNTS_TITLE));
        var filteredSuppAccountsData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(SUPPLIER_ACCOUNT_TITLE));
        var filteredExpensesData = items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(EXPENSES_TITLE));

        sumSales.set(filteredSalesData.mapToDouble(TreasuryBalance::getTotal_income).sum());
        sumPurchases.set(filteredPurchaseData.mapToDouble(TreasuryBalance::getTotal_output).sum());
        sumSalesRe.set(filteredSalesReData.mapToDouble(TreasuryBalance::getTotal_output).sum());
        sumPurchaseRe.set(filteredPurchaseReData.mapToDouble(TreasuryBalance::getTotal_income).sum());
        sumCustomerPaid.set(filteredCustomerAccountsData.mapToDouble(TreasuryBalance::getTotal_income).sum());
        sumSuppPaid.set(filteredSuppAccountsData.mapToDouble(TreasuryBalance::getTotal_output).sum());
        sumExpenses.set(filteredExpensesData.mapToDouble(TreasuryBalance::getTotal_output).sum());

        countInvoiceSales.set(items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(SALES_TITLE)).count());
        countInvoicePurchase.set(items.stream().filter(treasuryBalance -> treasuryBalance.getInformation().equals(PURCHASES_TITLE)).count());
    }

    private Predicate<TreasuryBalance> filterByUsers() {
        if (comboUsers.getSelectionModel().isEmpty()) return t2 -> true;
        if (comboUsers.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
        return treasuryBalance -> treasuryBalance.getUser_id() == getUsersByName().getId();
    }

    private Users getUsersByName() {
        try {
            return userService.getUsersByName(comboUsers.getSelectionModel().getSelectedItem());
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
        return new Users();
    }

    private Predicate<TreasuryBalance> filterByDate() {
        LocalDate dateFromValue = parseDate(dateFrom.getValue().toString());
        LocalDate dateToValue = parseDate(dateTo.getValue().toString());
        return t2 -> {
            LocalDate date = parseDate(t2.getDate().toString().substring(0, 10));
//            LocalTime time = LocalTime.parse(t2.getDate().toString().substring(11, 16));
            return isDateInRange(date, dateFromValue, dateToValue);
        };
    }

    private Predicate<TreasuryBalance> filterByTime() {
//        LocalTime timeFromValue = LocalTime.parse(dateFrom.getValue().toString().substring(11, 16));
//        return treasuryBalance -> treasuryBalance.getDate_insert().toString().substring(11, 16).equals(timePicker.getText());
//        var timeSearchController = openTimeSearchApplication.getTimeSearchController();

        return treasuryBalance -> true;
    }

    private Predicate<TreasuryBalance> filterByDetails() {
        if (comboDetails.getSelectionModel().isEmpty()) return t2 -> true;
        if (comboDetails.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
        return treasuryBalance -> treasuryBalance.getInformation().equals(comboDetails.getSelectionModel().getSelectedItem());
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return (date.isEqual(dateFrom) || date.isAfter(dateFrom)) && (date.isEqual(dateTo) || date.isBefore(dateTo));
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date);
    }

    private void updateFinancialSummaries() {
        sumIncome.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(tableView.getItems().stream().mapToDouble(TreasuryBalance::getTotal_income).sum())));
        sumOutput.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(tableView.getItems().stream().mapToDouble(TreasuryBalance::getTotal_output).sum())));
        sumBalance.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(tableView.getItems().stream().mapToDouble(TreasuryBalance::getBalance).sum())));
        sumData();
    }
}
