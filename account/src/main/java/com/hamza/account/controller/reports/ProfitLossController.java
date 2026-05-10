package com.hamza.account.controller.reports;

import com.hamza.account.controller.model.TableData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Earnings;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.EarningsService;
import com.hamza.account.service.UsersService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.NumberUtils;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "reports/profit-loss.fxml")
public class ProfitLossController {

    private final UsersService usersService = ServiceRegistry.get(UsersService.class);
    private final EarningsService earningsService = ServiceRegistry.get(EarningsService.class);
    @FXML
    private Button btnShow;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Label labelFrom, labelTo;
    @FXML
    private Label labelSales, labelSalesReturn, labelTotalReceipt, labelWithdrawals, labelBankBalance, labelExpenses, labelGrossProfit, labelNetCash, labelTreasury, labelCashDeposit;
    @FXML
    private Label labelTotalPurchase, labelTotalDamaged, labelTotalPaid, labelPaymentFees, labelTotalPurchaseReturn, labelTotalCost;
    @FXML
    private TableView<TableData> tableView;
    @FXML
    private TextField txtBankBalance, txtCashDeposit, txtExpenses, txtGrossProfit, txtNetCash;
    @FXML
    private TextField txtPaymentFees, txtSales, txtSalesReturn, txtTotalCost, txtTotalDamaged, txtTotalPaid, txtTotalPurchase;
    @FXML
    private TextField txtTotalPurchaseReturn, txtTotalReceipt, txtTreasury, txtWithdrawals, txtExpenseTreasury;
    @FXML
    private StackPane stackPane;
    @FXML
    private Text searchByDate;
    @FXML
    private ToolbarReportsNameController toolbarReportsNameController;
    private MaskerPaneSetting maskerPaneSetting;

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        otherSetting();
        nameSetting();
        action();
        getTable();
    }

    private void otherSetting() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, TableData.class);
        searchTable();
    }

    private void searchTable() {
        maskerPaneSetting.showMaskerPane(() -> {
            List<Earnings> allEarningsData;
            try {
                allEarningsData = earningsService.getEarningsByDateRange(dateFrom.getValue(), dateTo.getValue());


                List<TableData> tableData = new ArrayList<>();

                for (int i = 0; i < usersService.getUsersList().size(); i++) {
                    var user = usersService.getUsersList().get(i);
                    var list = allEarningsData.stream().filter(earnings -> earnings.getUsers().getId() == user.getId()).toList();

                    var sales = list.stream().filter(earnings -> earnings.getTable_id().equals("sales")).mapToDouble(Earnings::getTotal).sum();
                    var purchase = list.stream().filter(earnings -> earnings.getTable_id().equals("buy")).mapToDouble(Earnings::getTotal).sum();
                    // paid
                    var sales_paid = list.stream().filter(earnings -> earnings.getTable_id().equals("sales")).mapToDouble(Earnings::getPaid).sum();
                    var purchase_paid = list.stream().filter(earnings -> earnings.getTable_id().equals("buy")).mapToDouble(Earnings::getPaid).sum();
                    // returns
                    var sales_re = list.stream().filter(earnings -> earnings.getTable_id().equals("sales_re")).mapToDouble(Earnings::getTotal).sum();
                    var purchase_re = list.stream().filter(earnings -> earnings.getTable_id().equals("buy_re")).mapToDouble(Earnings::getTotal).sum();
                    // expenses
                    var expenses = list.stream().filter(earnings -> earnings.getTable_id().equals("expenses")).mapToDouble(Earnings::getTotal).sum();

                    // total cost = sales cost
                    var profit = list.stream().filter(earnings -> earnings.getTable_id().equals("sales")).mapToDouble(Earnings::getProfit).sum();
                    var profit_re = list.stream().filter(earnings -> earnings.getTable_id().equals("sales_re")).mapToDouble(Earnings::getProfit).sum();

                    double total_cost = sales - profit;

                    var deposit = list.stream().filter(earnings -> earnings.getTable_id().equals("deposit")).mapToDouble(Earnings::getTotal).sum();
                    var deposit_expenses = list.stream().filter(earnings -> earnings.getTable_id().equals("deposit_expenses")).mapToDouble(Earnings::getTotal).sum();

                    TableData tableDataEntry = new TableData();
                    tableDataEntry.setTotalSales(sales);
                    tableDataEntry.setTotalPurchase(purchase);
                    tableDataEntry.setTotalReceipt(sales_paid);
                    tableDataEntry.setTotalPaid(purchase_paid);
                    tableDataEntry.setTotalSalesReturn(sales_re);
                    tableDataEntry.setTotalPurchaseReturn(purchase_re);
                    tableDataEntry.setTotalExpense(expenses);
                    tableDataEntry.setUsername(user.getUsername());
                    tableDataEntry.setTotal_profit(profit - profit_re);
                    tableDataEntry.setTotal_cost(total_cost);
                    tableDataEntry.setAccount_customer(list.stream().filter(earnings -> earnings.getTable_id().equals("customers_accounts")).mapToDouble(Earnings::getTotal).sum());
                    tableDataEntry.setAccount_supplier(list.stream().filter(earnings -> earnings.getTable_id().equals("suppliers_accounts")).mapToDouble(Earnings::getTotal).sum());
                    tableDataEntry.setTotal_deposit(deposit);
                    tableDataEntry.setTotal_deposit_expense(deposit_expenses);
                    tableDataEntry.setTotal_balance(deposit - deposit_expenses);

                    tableData.add(tableDataEntry);
                }

                tableView.setItems(FXCollections.observableArrayList(tableData));
            } catch (DaoException e) {
                log.error(e.getMessage(), e.getCause());
                AllAlerts.alertError(e.getMessage());
                return;
            }
        });


        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
            var totalSalesSum = tableView.getItems().stream().mapToDouble(TableData::getTotalSales).sum();
            var totalSalesReturnSum = tableView.getItems().stream().mapToDouble(TableData::getTotalSalesReturn).sum();
            var totalReceiptSum = tableView.getItems().stream().mapToDouble(TableData::getTotalReceipt).sum();
            var totalPurchaseSum = tableView.getItems().stream().mapToDouble(TableData::getTotalPurchase).sum();
            var totalPurchaseReturnSum = tableView.getItems().stream().mapToDouble(TableData::getTotalPurchaseReturn).sum();
            var totalPaidAmount = tableView.getItems().stream().mapToDouble(TableData::getTotalPaid).sum();
            var totalExpenseSum = tableView.getItems().stream().mapToDouble(TableData::getTotalExpense).sum();
            var totalProfit = tableView.getItems().stream().mapToDouble(TableData::getTotal_profit).sum();
            var totalCost = tableView.getItems().stream().mapToDouble(TableData::getTotal_cost).sum();
            var totalAccountCustomer = tableView.getItems().stream().mapToDouble(TableData::getAccount_customer).sum();
            var totalAccountSupplier = tableView.getItems().stream().mapToDouble(TableData::getAccount_supplier).sum();
            var totalDeposit = tableView.getItems().stream().mapToDouble(TableData::getTotal_deposit).sum();
            var totalDepositExpenses = tableView.getItems().stream().mapToDouble(TableData::getTotal_deposit_expense).sum();
            var totalBalance = tableView.getItems().stream().mapToDouble(TableData::getTotal_balance).sum();

            txtSales.setText(totalSalesSum + "");
            txtSalesReturn.setText(totalSalesReturnSum + "");
            txtTotalReceipt.setText(totalReceiptSum + "");
            txtTotalPurchase.setText(totalPurchaseSum + "");
            txtTotalPurchaseReturn.setText(totalPurchaseReturnSum + "");
            txtTotalPaid.setText(totalPaidAmount + "");
            txtExpenses.setText(totalExpenseSum + "");
            txtGrossProfit.setText(totalProfit + "");
            txtTotalCost.setText(totalCost + "");
            txtWithdrawals.setText(totalAccountCustomer + "");
            txtPaymentFees.setText(totalAccountSupplier + "");
//            txtTreasury.setText(totalBalance + "");
//            txtCashDeposit.setText(totalDeposit + "");
            txtExpenseTreasury.setText(totalDepositExpenses + "");

            // cash
            txtNetCash.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces((totalReceiptSum + totalAccountCustomer + totalPurchaseReturnSum) - (totalPaidAmount + totalAccountSupplier + totalSalesReturnSum + totalExpenseSum))));
//            txtTreasury.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(totalDeposit - totalDepositExpenses)));
            txtTreasury.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(totalBalance)));
            txtCashDeposit.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(totalDeposit)));

        });
    }

    private void nameSetting() {
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        btnShow.setText(Setting_Language.WORD_SEARCH);
        searchByDate.setText(Setting_Language.WORD_SEARCH_DATE);

        // name totals
        labelSales.setText(Setting_Language.TOTAL_SALES);
        labelSalesReturn.setText(Setting_Language.TOTAL_SALES_RE);
        labelTotalReceipt.setText(Setting_Language.TOTAL_RECEIPT);
        labelBankBalance.setText(Setting_Language.BANK_BALANCE);
        labelExpenses.setText(Setting_Language.EXPENSES);
        labelGrossProfit.setText(Setting_Language.GROSS_PROFIT);
        labelNetCash.setText(Setting_Language.NET_CASH);
        labelTreasury.setText(Setting_Language.TREASURY);
        labelCashDeposit.setText(Setting_Language.CASH_DEPOSIT);
        labelTotalPurchase.setText(Setting_Language.TOTAL_PUR);
        labelTotalPurchaseReturn.setText(Setting_Language.TOTAL_PUR_RE);
        labelTotalPaid.setText(Setting_Language.WORD_PAID);
        labelTotalDamaged.setText(Setting_Language.TOTAL_DAMAGED);
        labelWithdrawals.setText("مدفوع ح/ العملاء");
        labelPaymentFees.setText("مدفوع ح/ الموردين");
        labelTotalCost.setText(Setting_Language.TOTAL_COST);


        toolbarReportsNameController.setReportToolbar(new ToolbarReportsNameInterface() {
            @Override
            public String setTitle() {
                return Setting_Language.WORD_PROFIT_LOSS;
            }

            @Override
            public void print() throws Exception {

            }

            @Override
            public void refresh() throws Exception {
                searchTable();
            }
        });
    }

    private void action() {
        btnShow.setOnAction(actionEvent -> {
            searchTable();
        });
    }
}

