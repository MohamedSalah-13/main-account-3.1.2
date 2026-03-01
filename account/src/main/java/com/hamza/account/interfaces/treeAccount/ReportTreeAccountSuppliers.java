package com.hamza.account.interfaces.treeAccount;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model_print.TreeAccountModelForPrint;
import com.hamza.account.controller.name_account.AccountDetailsController;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.account.otherSetting.SearchAccountByDate;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.dateTime.SearchByDate;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;


public class ReportTreeAccountSuppliers
        extends LoadData implements ReportTreeInterface<SupplierAccount, Suppliers> {

    private final DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> dataInterface;
    private final Print_Reports printReports;
    private final AccountData<SupplierAccount> accountData;
    private List<TreeAccountModelForPrint> listPrint;

    public ReportTreeAccountSuppliers(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.dataInterface = new SuppliersData(daoFactory, dataPublisher);
        this.printReports = new Print_Reports();
        this.accountData = new AccountSuppliers(daoFactory);
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
    public void addColumns(TreeTableView<SupplierAccount> treeView) {
        TreeTableColumn<SupplierAccount, String> tableDate = new TreeTableColumn<>(Setting_Language.WORD_NAME);
        tableDate.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getSuppliers();
            return theAccountsStringCellDataFeatures.getValue().getValue().getSuppliers().nameProperty();
        });
        treeView.getColumns().add(2, tableDate);

    }

    @Override
    public SupplierAccount loadTreeRoot() {
        return model(0, 0, 0);
    }

    @Override
    public TreeItem<SupplierAccount> treeItemMain(List<SupplierAccount> list) {
        double pur, paid, amount;
        pur = list.stream().mapToDouble(SupplierAccount::getPurchase).sum();
        paid = list.stream().mapToDouble(SupplierAccount::getPaid).sum();
        amount = pur - paid;
        return new TreeItem<>(model(roundToTwoDecimalPlaces(pur), roundToTwoDecimalPlaces(paid), roundToTwoDecimalPlaces(amount)));
    }

    @Override
    public void addItemInTree(TreeItem<SupplierAccount> treeItem, List<SupplierAccount> list) {

        try {
            listPrint = new ArrayList<>();
            treeItem.getChildren().clear();

            Set<String> dateSet = list.stream().map(SupplierAccount::getDate).sorted().collect(Collectors.toCollection(LinkedHashSet::new));

            List<String> arr = dateSet.stream().toList();
            for (int i = 0; i < dateSet.size(); i++) {
                int finalI = i;

                List<SupplierAccount> list_sum = list
                        .stream().filter(account_model -> account_model.getDate().equals(arr.get(finalI))).toList();
                double pur = roundToTwoDecimalPlaces(list_sum.stream().mapToDouble(SupplierAccount::getPurchase).sum());
                double paid = roundToTwoDecimalPlaces(list_sum.stream().mapToDouble(SupplierAccount::getPaid).sum());

                SupplierAccount model_day = new SupplierAccount();
                model_day.setDate(arr.get(i));
                model_day.setPurchase(pur);
                model_day.setPaid(paid);
                model_day.setAmount(roundToTwoDecimalPlaces(pur - paid));
                model_day.setSuppliers(new Suppliers(0));
                TreeItem<SupplierAccount> treeItemDate = new TreeItem<>(model_day);
//                treeItemDate.setExpanded(true);
                treeItem.getChildren().add(treeItemDate);
                addTreeDate(list_sum, treeItemDate);
            }
        } catch (DaoException e) {
            AllAlerts.alertError(e.getMessage());
        }
    }

    @Override
    public List<SupplierAccount> listTree(String dateForm, String dateTo) throws Exception {
        return dataInterface.nameAndAccountInterface().accountList()
                .stream().filter(e -> e.getDate().compareTo(dateForm) >= 0 && e.getDate().compareTo(dateTo) <= 0).toList();
    }

    @Override
    public boolean filterListByName(SupplierAccount supplierAccount, String name) {
//        return theAccounts -> theAccounts.getSuppliers().getName().equals(name);
        return supplierAccount.getSuppliers().getName().equals(name);
    }

    @Override
    public boolean filterListByTableName(SupplierAccount supplierAccount, String name) {
        return supplierAccount.getInformation_name().equals(name);
    }

    @Override
    public String nameTitle() {
        return Setting_Language.WORD_REPORT_SUPP;
    }

    @Override
    public void print() {
        printReports.printAccountByNameOrDate(listPrint, false, dataInterface.designInterface().nameTextOfReport(), null);
    }

    @Override
    public List<Suppliers> listNames() throws Exception {
        return dataInterface.nameAndAccountInterface().nameList();
    }

    @Override
    public void print_totals() throws Exception {
        List<SupplierAccount> list = dataInterface.nameAndAccountInterface().accountTotalList(null, null).stream()
                .filter(e -> e.getAmount() != 0).sorted(Comparator.comparing(e -> e.getSuppliers().getName())).toList();

        List<TreeAccountModelForPrint> accountModelForPrints = new ArrayList<>();
        list.forEach(supplierAccount -> accountModelForPrints.add(new TreeAccountModelForPrint(supplierAccount.getSuppliers().getId(), supplierAccount.getSuppliers().getName()
                , supplierAccount.getDate(), supplierAccount.getPurchase(), supplierAccount.getPaid(), supplierAccount.getAmount(), supplierAccount.getNotes())));
        printReports.printTotalsAccounts(accountModelForPrints, null);
    }

    @Override
    public boolean showData() {
        return true;
    }

    @Override
    public boolean colorRow(SupplierAccount supplierAccount) {
        return supplierAccount.getSuppliers().getId() == 0;
    }

    private SearchByDate<SupplierAccount> searchByDate(List<SupplierAccount> list, String date1, String date2) {
        return new SearchAccountByDate<>(list, date1, date2, accountData);
    }

    private SupplierAccount model(double pur, double paid, double amount) {
        SupplierAccount model = new SupplierAccount();
        model.setDate(LocalDate.now().toString());
        model.setPurchase(pur);
        model.setPaid(paid);
        model.setAmount(amount);
        model.setSuppliers(new Suppliers(0, Setting_Language.WORD_NAME));
        return model;
    }

    private void addTreeDate(List<SupplierAccount> list, TreeItem<SupplierAccount> item) throws DaoException {
        list = list.stream().sorted(Comparator.comparing(SupplierAccount::getDate)).toList();

        for (SupplierAccount accountModel : list) {
            SupplierAccount model = new SupplierAccount();

            int num = accountModel.getId();
            if (num > 0)
                model.setId(num);

            model.setDate(accountModel.getDate());
            model.setPurchase(accountModel.getPurchase());
            model.setPaid(accountModel.getPaid());
            model.setNotes(accountModel.getNotes());
            model.setSuppliers(accountModel.getSuppliers());
            model.setAmount(accountModel.getAmount());
            model.setInformation_name(accountModel.getInformation_name());
            TreeItem<SupplierAccount> treeItem = new TreeItem<>(model);
            item.getChildren().add(treeItem);

            TreeAccountModelForPrint treeAccountModelForPrint = new TreeAccountModelForPrint(model.getSuppliers().getId(), model.getSuppliers().getName()
                    , model.getDate(), model.getPurchase(), model.getPaid(), model.getAmount(), model.getNotes());
            listPrint.add(treeAccountModelForPrint);
        }
    }

}
