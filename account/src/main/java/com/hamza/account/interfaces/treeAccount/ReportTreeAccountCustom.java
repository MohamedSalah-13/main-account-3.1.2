package com.hamza.account.interfaces.treeAccount;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.TreeAccountModelForPrint;
import com.hamza.account.controller.name_account.AccountDetailsController;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class ReportTreeAccountCustom extends LoadData implements ReportTreeInterface<CustomerAccount, Customers> {

    private final DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterface;
    private final Print_Reports printReports;
    private List<TreeAccountModelForPrint> listPrint;
    private final Set<TreeItem<CustomerAccount>> lazyLoadedItems = Collections.newSetFromMap(new IdentityHashMap<>());

    public ReportTreeAccountCustom(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.dataInterface = new CustomData(daoFactory, dataPublisher);
        this.printReports = new Print_Reports();
    }

    @Override
    public String styleSheet() {
        return dataInterface.designInterface().styleSheet();
    }

    @Override
    public List<Column<?>> getColumnDefinitions() {
        return AccountDetailsController.initializeAccountColumnDefinitions();
    }

    @Override
    public void addColumns(TreeTableView<CustomerAccount> treeView) {
        TreeTableColumn<CustomerAccount, String> tableDate = new TreeTableColumn<>(Setting_Language.WORD_NAME);
        tableDate.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getCustomers();
            return theAccountsStringCellDataFeatures.getValue().getValue().getCustomers().nameProperty();
        });

        treeView.getColumns().add(2, tableDate);
    }

    @Override
    public CustomerAccount loadTreeRoot() {
        return model(0, 0, 0);
    }

    @Override
    public TreeItem<CustomerAccount> treeItemMain(List<CustomerAccount> list) {
        double pur, paid, amount;
        pur = list.stream().mapToDouble(CustomerAccount::getPurchase).sum();
        paid = list.stream().mapToDouble(CustomerAccount::getPaid).sum();
        amount = pur - paid;
        return new TreeItem<>(model(roundToTwoDecimalPlaces(pur), roundToTwoDecimalPlaces(paid), roundToTwoDecimalPlaces(amount)));
    }

    @Override
    public void addItemInTree(TreeItem<CustomerAccount> treeItem, List<CustomerAccount> list) {

        listPrint = new ArrayList<>();
        treeItem.getChildren().clear();
        lazyLoadedItems.clear();

        for (CustomerAccount accountModel : list) {
            TreeAccountModelForPrint treeAccountModelForPrint = new TreeAccountModelForPrint(accountModel.getCustomers().getId(),
                    accountModel.getCustomers().getName(), accountModel.getDate(), accountModel.getPurchase(),
                    accountModel.getPaid(), accountModel.getAmount(), accountModel.getNotes());
            listPrint.add(treeAccountModelForPrint);
        }

        Set<String> dateSet = list.stream().map(CustomerAccount::getDate).sorted().collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> arr = dateSet.stream().toList();
        for (int i = 0; i < dateSet.size(); i++) {
            int finalI = i;

            List<CustomerAccount> list_sum = list
                    .stream().filter(account_model -> account_model.getDate().equals(arr.get(finalI))).toList();
            double pur = roundToTwoDecimalPlaces(list_sum.stream().mapToDouble(CustomerAccount::getPurchase).sum());
            double paid = roundToTwoDecimalPlaces(list_sum.stream().mapToDouble(CustomerAccount::getPaid).sum());

            CustomerAccount customerAccount = new CustomerAccount();
            customerAccount.setDate(arr.get(i));
            customerAccount.setPurchase(pur);
            customerAccount.setPaid(paid);
            customerAccount.setAmount(roundToTwoDecimalPlaces(pur - paid));
            customerAccount.setCustomers(new Customers(0));
            TreeItem<CustomerAccount> treeItemDate = new TreeItem<>(customerAccount);
//                treeItemDate.setExpanded(true);
            treeItem.getChildren().add(treeItemDate);

            treeItemDate.getChildren().add(new TreeItem<>(new CustomerAccount()));
            treeItemDate.expandedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue && !lazyLoadedItems.contains(treeItemDate)) {
                    treeItemDate.getChildren().clear();
                    try {
                        addTreeDate(list_sum, treeItemDate);
                    } catch (DaoException e) {
                        AllAlerts.alertError(e.getMessage());
                    }
                    lazyLoadedItems.add(treeItemDate);
                }
            });
        }
    }

    @Override
    public List<CustomerAccount> listTree(String dateForm, String dateTo) throws Exception {
        return dataInterface.nameAndAccountInterface().accountList()
                .stream().filter(e -> e.getDate().compareTo(dateForm) >= 0 && e.getDate().compareTo(dateTo) <= 0).toList();
    }

    @Override
    public boolean filterListByName(CustomerAccount customerAccount, String name) {
//        return theAccounts -> theAccounts.getCustomers().getName().equals(name);
        return customerAccount.getCustomers().getName().equals(name);
    }

    @Override
    public boolean filterListByTableName(CustomerAccount customerAccount, String name) {
        return customerAccount.getInformation_name().equals(name);
    }

    @Override
    public String nameTitle() {
        return Setting_Language.WORD_REPORT_CUSTOMER;
    }

    @Override
    public void print() {
        printReports.printAccountByNameOrDate(listPrint, false, dataInterface.designInterface().nameTextOfReport(), null);
    }

    @Override
    public List<Customers> listNames() throws Exception {
        return dataInterface.nameAndAccountInterface().nameList();
    }

    @Override
    public void print_totals() throws Exception {
        List<CustomerAccount> list = dataInterface.nameAndAccountInterface().accountTotalList(null, null).stream()
                .filter(e -> e.getAmount() != 0).sorted(Comparator.comparing(e -> e.getCustomers().getName())).toList();
        List<TreeAccountModelForPrint> accountModelForPrints = new ArrayList<>();
        list.forEach(customerAccount -> accountModelForPrints.add(new TreeAccountModelForPrint(customerAccount.getCustomers().getId(), customerAccount.getCustomers().getName()
                , customerAccount.getDate(), customerAccount.getPurchase(), customerAccount.getPaid(), customerAccount.getAmount(), customerAccount.getNotes())));
        printReports.printTotalsAccounts(accountModelForPrints, null);
    }

    @Override
    public boolean showData() {
        return true;
    }

    @Override
    public boolean colorRow(CustomerAccount customerAccount) {
        return customerAccount.getCustomers().getId() == 0;
    }


    private CustomerAccount model(double pur, double paid, double amount) {
        CustomerAccount model = new CustomerAccount();
        model.setDate(LocalDate.now().toString());
        model.setPurchase(pur);
        model.setPaid(paid);
        model.setAmount(amount);
        model.setCustomers(new Customers(0, Setting_Language.WORD_NAME));
        return model;
    }

    private void addTreeDate(List<CustomerAccount> list, TreeItem<CustomerAccount> item) throws DaoException {
        list = list.stream().sorted(Comparator.comparing(CustomerAccount::getDate)).toList();

        for (CustomerAccount accountModel : list) {
            CustomerAccount model = new CustomerAccount();

            int num = accountModel.getId();
            if (num > 0) {
                model.setId(num);
            }
            model.setDate(accountModel.getDate());
            model.setPurchase(accountModel.getPurchase());
            model.setPaid(accountModel.getPaid());
            model.setNotes(accountModel.getNotes());
            model.setCustomers(accountModel.getCustomers());
            model.setAmount(accountModel.getAmount());
            model.setInformation_name(accountModel.getInformation_name());
            TreeItem<CustomerAccount> treeItem = new TreeItem<>(model);
            item.getChildren().add(treeItem);

        }
    }

}
