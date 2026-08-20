package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsSalesData;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.service.TotalSalesService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

@RequiredArgsConstructor
public class TotalSalesImpDesign implements TotalDesignInterface<Total_Sales> {

    private final DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterface;
    private final TotalSalesService totalSalesService;

    @Override
    public void getTable(TableView<Total_Sales> tableView) {
        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> cellName = f -> f.getValue().getCustomers().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("type"), 3, colNameType);


        Callback<TableColumn.CellDataFeatures<Total_Sales, String>, ObservableValue<String>> colDelegate = f -> f.getValue().getEmployeeObject().nameProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("user.type.delegate"), tableView.getColumns().size(), colDelegate);

        if (AuthorizationGuard.isGranted(AppPermissions.INVOICE_PROFIT_SHOW)) {
            Callback<TableColumn.CellDataFeatures<Total_Sales, Double>, ObservableValue<Double>> totalProfit =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getTotal_profit()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.invoice.profit"), tableView.getColumns().size(), totalProfit);

            Callback<TableColumn.CellDataFeatures<Total_Sales, Double>, ObservableValue<Double>> totalProfitPercent =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getProfit_percent()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.profit.percent"), tableView.getColumns().size(), totalProfitPercent);
        }
    }

    @Override
    public List<Total_Sales> dataList() throws Exception {
        return totalSalesService.getListByCurrentMonth();
    }

    @Override
    public List<TableColumn<Total_Sales, ?>> columns() {
        return List.of();
    }


    @Override
    public TotalsDataInterface<Total_Sales> totalsDataInterface() {
        return new TotalsSalesData();
    }

    @Override
    public int deleteData(int id) throws DaoException {
        return totalSalesService.deleteById(id);
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return totalSalesService.deleteMultiData(ids);
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
