package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsSalesData;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.type.InvoiceType;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

public record TotalSalesImpDesign(DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterface,
                                  ServiceData serviceData) implements TotalDesignInterface<Total_Sales> {

    @Override
    public void getTable(TableView<Total_Sales> tableView) {
        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> cellName = f -> f.getValue().getCustomers().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, Setting_Language.WORD_TYPE, 3, colNameType);

        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> colStockName = f -> f.getValue().getStockData().nameProperty();
        addColumn(tableView, Setting_Language.WORD_STOCK, tableView.getColumns().size(), colStockName);

        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> colDelegate = f -> f.getValue().getEmployeeObject().nameProperty();
        addColumn(tableView, Setting_Language.DELEGATE, tableView.getColumns().size(), colDelegate);

        var b = LogApplication.usersVo.getId() == 1;
        if (b) {
            Callback<TableColumn.CellDataFeatures<Total_Sales, Double>, ObservableValue<Double>> totalProfit =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getTotal_profit()).asObject();
            addColumn(tableView, "ربح الفاتورة", tableView.getColumns().size(), totalProfit);

            Callback<TableColumn.CellDataFeatures<Total_Sales, Double>, ObservableValue<Double>> totalProfitPercent =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getProfit_percent()).asObject();
            addColumn(tableView, "الربح نسبة", tableView.getColumns().size(), totalProfitPercent);
        }
    }

    @Override
    public List<Total_Sales> dataList() throws Exception {
        return serviceData.getTotalSalesService().getListByCurrentMonth();
    }

    @Override
    public Class<? super Total_Sales> classForColumn() {
        return Total_Sales.class;
    }


    @Override
    public TotalsDataInterface<Total_Sales> totalsDataInterface() {
        return new TotalsSalesData();
    }

    @Override
    public int deleteData(Total_Sales totalSales) throws DaoException {
        return dataInterface.totalsAndPurchaseList().totalDao().deleteById(totalSales.getId());
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return serviceData.getTotalSalesService().deleteMultiData(ids);
    }


    @Override
    public Predicate<Total_Sales> filterById(int id) {
        return totalSales -> totalSales.getCustomers().getId() == id;
    }


    @Override
    public Predicate<Total_Sales> filterByName(String name) {
        return totalSales -> totalSales.getCustomers().getName().equals(name);

    }


    @Override
    public Predicate<Total_Sales> filterByDelegate(String name) {
        return totalSales -> totalSales.getEmployeeObject().getName().equals(name);
    }

    @Override
    public Predicate<Total_Sales> filterByInvoiceType(InvoiceType type) {
        return totalSales -> totalSales.getInvoiceType() == type;
    }


    @Override
    public WriteExcelInterface<Total_Sales> writeExcelInterface(List<Total_Sales> items) {
        return new WriteExcelInterface<>() {

            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE
                        , Setting_Language.WORD_DATE
                        , Setting_Language.WORD_NAME
                        , Setting_Language.WORD_TYPE
                        , Setting_Language.WORD_TOTAL
                        , Setting_Language.TOTAL_DISCOUNT
                        , Setting_Language.THE_AMOUNT
                        , Setting_Language.WORD_PAID
                        , Setting_Language.WORD_REST
                };
            }


            @NotNull
            @Override
            public Object[] dataRow(Total_Sales totalBuy) {
                return new Object[]{totalBuy.getId()
                        , totalBuy.getDate()
                        , totalBuy.customersProperty().get().getName()
                        , totalBuy.getInvoiceType().getType()
                        , totalBuy.getTotal()
                        , totalBuy.getDiscount()
                        , totalBuy.getTotal_after_discount()
                        , totalBuy.getPaid()
                        , totalBuy.getRest()
                };
            }


            @NotNull
            @Override
            public List<Total_Sales> itemsList() {
                return items;
            }

            @Override
            public boolean addDataToFile() {
                return true;
            }


            @NotNull
            @Override
            public String sheetName() {
                return dataInterface.designInterface().nameTextOfTotal();
            }
        };
    }

}
