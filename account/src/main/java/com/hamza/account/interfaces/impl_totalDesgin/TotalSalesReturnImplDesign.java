package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsSalesReturnData;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.service.TotalSalesReturnService;
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
public class TotalSalesReturnImplDesign implements TotalDesignInterface<Total_Sales_Re> {
    private final DataInterface<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> dataInterface;
    private final TotalSalesReturnService totalSalesReturnService;

    @Override
    public void getTable(TableView<Total_Sales_Re> tableView) {
        Callback<TableColumn.CellDataFeatures<Total_Sales_Re, String>, ObservableValue<String>> cellName = f -> f.getValue().getCustomer().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<Total_Sales_Re, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("type"), 3, colNameType);


        Callback<TableColumn.CellDataFeatures<Total_Sales_Re, String>, ObservableValue<String>> colDelegate = f -> f.getValue().getEmployeeObject().nameProperty();
        addColumn(tableView, LanguageManager.getInstance().getString("user.type.delegate"), tableView.getColumns().size(), colDelegate);

        if (AuthorizationGuard.isGranted(AppPermissions.INVOICE_PROFIT_SHOW)) {
            Callback<TableColumn.CellDataFeatures<Total_Sales_Re, Double>, ObservableValue<Double>> totalProfit =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getTotal_profit()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.invoice.profit"), tableView.getColumns().size(), totalProfit);

            Callback<TableColumn.CellDataFeatures<Total_Sales_Re, Double>, ObservableValue<Double>> totalProfitPercent =
                    cellData -> new SimpleDoubleProperty(cellData.getValue().getProfit_percent()).asObject();
            addColumn(tableView, LanguageManager.getInstance().getString("report.column.profit.percent"), tableView.getColumns().size(), totalProfitPercent);
        }

    }

    @Override
    public List<Total_Sales_Re> dataList() throws Exception {
        return totalSalesReturnService.getListByCurrentMonth();
    }

    @Override
    public @NotNull Class<? super Total_Sales_Re> classForColumn() {
        return Total_Sales_Re.class;
    }

    @NotNull
    @Override
    public TotalsDataInterface<Total_Sales_Re> totalsDataInterface() {
        return new TotalsSalesReturnData();
    }

    @Override
    public int deleteData(Total_Sales_Re totalSalesRe) throws DaoException {
        return totalSalesReturnService.deleteById(Math.toIntExact(totalSalesRe.getId()));
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return totalSalesReturnService.deleteMultiData(ids);
    }

    @NotNull
    @Override
    public WriteExcelInterface<Total_Sales_Re> writeExcelInterface(List<Total_Sales_Re> items) {
        return new WriteExcelInterface<>() {
            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE
                        , Setting_Language.WORD_DATE
                        , Setting_Language.WORD_NAME
                        , Setting_Language.WORD_TOTAL
                };
            }

            @NotNull
            @Override
            public Object[] dataRow(Total_Sales_Re totalBuy) {
                return new Object[]{totalBuy.getId()
                        , totalBuy.getDate()
                        , totalBuy.getCustomer().getName()
                        , totalBuy.getTotal()
                };
            }

            @NotNull
            @Override
            public List<Total_Sales_Re> itemsList() {
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
