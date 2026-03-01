package com.hamza.account.interfaces.treePurchase;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model_print.PrintPurchaseWithName;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hamza.account.interfaces.treePurchase.ReportTreeSales.createSalesReportColumns;
import static com.hamza.controlsfx.language.Setting_Language.REPORT_PURCHASE;
import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class ReportTreePurchase extends LoadOtherData<Purchase, Total_buy, Suppliers, SupplierAccount> implements ReportTreeInterface<Purchase, Suppliers> {

    private List<Purchase> listPrint;
    private String date1;
    private String date2;

    public ReportTreePurchase(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.listPrint = new ArrayList<>();
    }

    @Override
    public String styleSheet() {
        return dataInterface.designInterface().styleSheet();
    }

    @Override
    public List<Column<?>> getColumnDefinitions() {
        return createSalesReportColumns();
    }

    @Override
    public void addColumns(TreeTableView<Purchase> treeView) {
        // add column barcode
        TreeTableColumn<Purchase, String> barcode = new TreeTableColumn<>(Setting_Language.WORD_BARCODE);
        barcode.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getItems();
            return theAccountsStringCellDataFeatures.getValue().getValue().getItems().barcodeProperty();
        });
        treeView.getColumns().add(1, barcode);

        // add column name
        TreeTableColumn<Purchase, String> tableDate = new TreeTableColumn<>(Setting_Language.WORD_NAME);
        tableDate.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getItems();
            return theAccountsStringCellDataFeatures.getValue().getValue().getItems().nameItemProperty();
        });
        treeView.getColumns().add(2, tableDate);

        TreeTableColumn<Purchase, String> tableUnit = new TreeTableColumn<>(Setting_Language.Unit);
        tableUnit.setCellValueFactory(theAccountsStringCellDataFeatures ->
                theAccountsStringCellDataFeatures.getValue().getValue().getUnitsType().unit_nameProperty());
        treeView.getColumns().add(3, tableUnit);

    }

    @Override
    public Purchase loadTreeRoot() {
        var purchase = new Purchase();
        ItemsModel items = new ItemsModel();
        items.setBarcode("0");
        items.setNameItem(LocalDate.now().toString());
        purchase.setItems(items);
        purchase.setPrice(0);
        purchase.setQuantity(0);
        purchase.setTotal(0);
        purchase.setUnitsType(new UnitsModel(1, "", 20));
        return purchase;
    }

    @Override
    public TreeItem<Purchase> treeItemMain(List<Purchase> list) {
        var purchase = new Purchase();
        double dis = roundToTwoDecimalPlaces(list.stream().mapToDouble(Purchase::getDiscount).sum());
        double total = roundToTwoDecimalPlaces(list.stream().mapToDouble(Purchase::getTotal).sum());
        double after = roundToTwoDecimalPlaces(list.stream().mapToDouble(Purchase::getTotal_after_discount).sum());
        purchase.setDiscount(dis);
        purchase.setTotal(total);
        purchase.setTotal_after_discount(after);
        purchase.setUnitsType(new UnitsModel(1, "", 20));
        purchase.setItems(new ItemsModel());
        return new TreeItem<>(purchase);
    }

    @Override
    public void addItemInTree(TreeItem<Purchase> treeItem, List<Purchase> list) {
        List<Integer> dateSet = list.stream().map(Purchase::getInvoiceNumber).sorted().collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        this.listPrint = new ArrayList<>();
        for (Integer integer : dateSet) {
            List<Purchase> purchaseList = list.stream().filter(purchase1 -> purchase1.getInvoiceNumber() == integer).toList();
            if (!purchaseList.isEmpty()) {
                double dis = roundToTwoDecimalPlaces(purchaseList.stream().mapToDouble(Purchase::getDiscount).sum());
                double total = roundToTwoDecimalPlaces(purchaseList.stream().mapToDouble(Purchase::getTotal).sum());
                double after = roundToTwoDecimalPlaces(purchaseList.stream().mapToDouble(Purchase::getTotal_after_discount).sum());
                Purchase mainPurchaseTree = new Purchase();
                int id = purchaseList.getFirst().getInvoiceNumber();

                mainPurchaseTree.setInvoiceNumber(id);
                mainPurchaseTree.setDiscount(dis);
                mainPurchaseTree.setTotal(total);
                mainPurchaseTree.setTotal_after_discount(after);
                mainPurchaseTree.setUnitsType(new UnitsModel(1, "", 20));

                var totalById = getTotalById(id);
                ItemsModel itemsModel = new ItemsModel();
                itemsModel.setBarcode(totalById.getDate());
                itemsModel.setNameItem(totalById.getSupplierData().getName());
                mainPurchaseTree.setItems(itemsModel);

                TreeItem<Purchase> treeItemDate = new TreeItem<>(mainPurchaseTree);
                treeItem.getChildren().add(treeItemDate);

                // add children
                for (Purchase purchase : purchaseList) {
                    TreeItem<Purchase> treePurchase = new TreeItem<>(purchase);
                    treeItemDate.getChildren().add(treePurchase);
                    listPrint.add(purchase);
                }
            }
        }
    }

    @Override
    public List<Purchase> listTree(String dateForm, String dateTo) throws Exception {
        var list = totalBuyService.getTotalPurchaseByDateRange(dateForm, dateTo)
                .stream()
                .map(Total_buy::getId)
                .sorted()
                .toList();
        // make list index order
        return dataInterface.totalsAndPurchaseList().purchaseOrSalesList(list.getFirst(), list.getLast());
    }

    @Override
    public boolean filterListByName(Purchase purchase, String name) {
        return getTotalById(purchase.getInvoiceNumber()).getSupplierData().getName().equals(name);
    }

    @Override
    public boolean filterListByTableName(Purchase purchase, String name) {
        return false;
    }

    @Override
    public String nameTitle() {
        return REPORT_PURCHASE;
    }

    @Override
    public void print() throws Exception {
        try {
            List<PrintPurchaseWithName> printPurchaseWithNames = new ArrayList<>();
            for (Purchase value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                var totalById = getTotalById(value.getInvoiceNumber());
                purchase.setNum(value.getInvoiceNumber());
                purchase.setName(totalById.getSupplierData().getName());
                purchase.setDate(totalById.getDate());
                purchase.setPrice(value.getPrice());
                purchase.setDiscount(value.getDiscount());
                purchase.setQuantity(value.getQuantity());
                purchase.setTotal(value.getTotal());
                purchase.setUnitsType(value.getUnitsType());
                purchase.setItemName(value.getItems().getNameItem());
                printPurchaseWithNames.add(purchase);

            }

            Optional<String> min = printPurchaseWithNames.stream().map(PrintPurchaseWithName::getDate).min(String::compareTo);
            Optional<String> max = printPurchaseWithNames.stream().map(PrintPurchaseWithName::getDate).max(String::compareTo);
            if (date1 == null) min.ifPresent(s -> date1 = s);
            if (date2 == null) max.ifPresent(s -> date2 = s);

            if (listPrint.isEmpty()) {
                throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            }

            new Print_Reports().printMultiInvoice(printPurchaseWithNames, nameTitle(), date1, date2, null);

        } catch (NullPointerException e) {
            throw new Exception(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }
    }

    @Override
    public List<Suppliers> listNames() throws Exception {
        return dataInterface.nameAndAccountInterface().nameList();
    }

    @Override
    public boolean colorRow(Purchase purchase) {
        return purchase.getPrice() == 0;
    }


    private Total_buy getTotalById(int i) {
        try {
            return totalBuyService.getTotalBuyById(i);
        } catch (DaoException e) {
            log.error(e.getMessage());
            return null;
        }
    }

}