package com.hamza.account.interfaces.treePurchase;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.TotalSalesService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class ReportTreeSales implements ReportTreeInterface<Sales, Customers> {

    private final DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterface;
    private final TotalSalesService totalSalesService;
    private List<Sales> listPrint;
    private String date1;
    private String date2;
    private final Set<TreeItem<Sales>> lazyLoadedItems = Collections.newSetFromMap(new IdentityHashMap<>());

    public ReportTreeSales(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        ServiceData serviceData = new ServiceData(daoFactory);
        this.listPrint = new ArrayList<>();
        this.dataInterface = new CustomData(daoFactory, dataPublisher);
        this.totalSalesService = serviceData.getTotalSalesService();
    }

    public static ArrayList<Column<?>> createSalesReportColumns() {
        return new ArrayList<>(Arrays.asList(new Column<>(Integer.class, "id", Setting_Language.WORD_CODE)
//                , new Column<>(String.class, "typeName", Setting_Language.WORD_TYPE)
                , new Column<>(Double.class, "price", Setting_Language.WORD_PRICE)
                , new Column<>(Double.class, "quantity", Setting_Language.WORD_QUANTITY)
                , new Column<>(Double.class, "total", Setting_Language.TOTAL)
                , new Column<>(Double.class, "discount", Setting_Language.WORD_DISCOUNT)
                , new Column<>(Double.class, "total_after_discount", Setting_Language.WORD_TOTAL)));
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
    public void addColumns(TreeTableView<Sales> treeView) {

        TreeTableColumn<Sales, String> barcode = new TreeTableColumn<>(Setting_Language.WORD_BARCODE);
        barcode.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getItems();
            return theAccountsStringCellDataFeatures.getValue().getValue().getItems().barcodeProperty();
        });
        treeView.getColumns().add(1, barcode);

        TreeTableColumn<Sales, String> tableDate = new TreeTableColumn<>(Setting_Language.WORD_NAME);
        tableDate.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            theAccountsStringCellDataFeatures.getValue().getValue().getItems();
            return theAccountsStringCellDataFeatures.getValue().getValue().getItems().nameItemProperty();
        });

        treeView.getColumns().add(2, tableDate);

        TreeTableColumn<Sales, String> tableUnit = new TreeTableColumn<>(Setting_Language.Unit);
        tableUnit.setCellValueFactory(theAccountsStringCellDataFeatures -> {
            var unitName = theAccountsStringCellDataFeatures.getValue().getValue().getUnitsType().getUnit_name();
            return unitName == null ? new ReadOnlyStringWrapper("") : new ReadOnlyStringWrapper(unitName);
        });

        treeView.getColumns().add(3, tableUnit);
    }

    @Override
    public Sales loadTreeRoot() {
        Sales sales = new Sales();
        ItemsModel items = new ItemsModel();
        items.setBarcode("0");
        items.setNameItem(LocalDate.now().toString());
        sales.setItems(items);
        sales.setPrice(0);
        sales.setQuantity(0);
        sales.setTotal(0);
        sales.setUnitsType(new UnitsModel(1, "", 20));
        return sales;
    }

    @Override
    public TreeItem<Sales> treeItemMain(List<Sales> list) {
        Sales sales = new Sales();
        double dis = roundToTwoDecimalPlaces(list.stream().mapToDouble(Sales::getDiscount).sum());
        double total = roundToTwoDecimalPlaces(list.stream().mapToDouble(Sales::getTotal).sum());
        double after = roundToTwoDecimalPlaces(list.stream().mapToDouble(Sales::getTotal_after_discount).sum());
        sales.setDiscount(dis);
        sales.setTotal(total);
        sales.setTotal_after_discount(after);
        sales.setUnitsType(new UnitsModel(1, "", 20));
        sales.setItems(new ItemsModel());
        return new TreeItem<>(sales);
    }

    @Override
    public void addItemInTree(TreeItem<Sales> treeItem, List<Sales> list) {
        List<Integer> dateSet = list.stream().map(Sales::getInvoiceNumber).sorted().collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        this.listPrint = new ArrayList<>(list);
        lazyLoadedItems.clear();
        for (Integer integer : dateSet) {
            List<Sales> salesList = list.stream().filter(sales -> sales.getInvoiceNumber() == integer).toList();
            if (!salesList.isEmpty()) {
                double dis = roundToTwoDecimalPlaces(salesList.stream().mapToDouble(Sales::getDiscount).sum());
                double total = roundToTwoDecimalPlaces(salesList.stream().mapToDouble(Sales::getTotal).sum());
                double after = roundToTwoDecimalPlaces(salesList.stream().mapToDouble(Sales::getTotal_after_discount).sum());
                Sales mainSalesTree = new Sales();
                int id = salesList.getFirst().getInvoiceNumber();
                mainSalesTree.setInvoiceNumber(id);
                mainSalesTree.setDiscount(dis);
                mainSalesTree.setTotal(total);
                mainSalesTree.setTotal_after_discount(after);
                mainSalesTree.setUnitsType(new UnitsModel(1, "", 20));

                // add date and name
                var totalById = getTotalById(mainSalesTree.getInvoiceNumber());
                ItemsModel items = new ItemsModel();
                items.setBarcode(totalById.getDate());
                items.setNameItem(totalById.getCustomers().getName());
                mainSalesTree.setItems(items);

                TreeItem<Sales> treeItemDate = new TreeItem<>(mainSalesTree);
                treeItem.getChildren().add(treeItemDate);

                treeItemDate.getChildren().add(new TreeItem<>(new Sales()));
                treeItemDate.expandedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue && !lazyLoadedItems.contains(treeItemDate)) {
                        treeItemDate.getChildren().clear();
                        for (Sales sales : salesList) {
                            TreeItem<Sales> treePurchase = new TreeItem<>(sales);
                            treeItemDate.getChildren().add(treePurchase);
                        }
                        lazyLoadedItems.add(treeItemDate);
                    }
                });
            }
        }
    }

    @Override
    public List<Sales> listTree(String dateForm, String dateTo) throws Exception {
        var salesByDateRange = totalSalesService.getTotalSalesByDateRange(dateForm, dateTo)
                .stream()
                .map(Total_Sales::getId)
                .sorted()
                .toList();
        // make list index order
        return dataInterface.totalsAndPurchaseList().purchaseOrSalesList(salesByDateRange.getFirst(), salesByDateRange.getLast());
    }

    @Override
    public boolean filterListByName(Sales sales, String name) {
        return getTotalById(sales.getInvoiceNumber()).getCustomers().getName().equals(name);
    }

    @Override
    public boolean filterListByTableName(Sales sales, String name) {
        return false;
    }

    @Override
    public String nameTitle() {
        return Setting_Language.REPORT_SALES;
    }

    @Override
    public void print() throws Exception {
        try {
            List<PrintPurchaseWithName> printPurchaseWithNames = new ArrayList<>();
            for (Sales value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                Total_Sales totalSales = getTotalById(value.getInvoiceNumber());
                if (totalSales != null) {
                    purchase.setNum(value.getInvoiceNumber());
                    purchase.setName(totalSales.getCustomers().getName());
                    purchase.setDate(totalSales.getDate());
                    purchase.setPrice(value.getPrice());
                    purchase.setDiscount(value.getDiscount());
                    purchase.setQuantity(value.getQuantity());
                    purchase.setTotal(value.getTotal());
                    purchase.setUnitsType(value.getUnitsType());
                    purchase.setItemName(value.getItems().getNameItem());
                    printPurchaseWithNames.add(purchase);
                }
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
    public List<Customers> listNames() throws Exception {
        return dataInterface.nameAndAccountInterface().nameList();
    }

    @Override
    public boolean colorRow(Sales sales) {
        return sales.getPrice() == 0;
    }

    private Total_Sales getTotalById(int salesId) {
        try {
            return totalSalesService.getTotalSalesById(salesId);
        } catch (DaoException e) {
            log.error(e.getMessage());
            return null;
        }
    }

}
