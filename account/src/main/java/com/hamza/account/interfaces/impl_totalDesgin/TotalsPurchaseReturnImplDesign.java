package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsBuyReturnData;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.service.TotalBuyReturnService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

@Log4j2
@RequiredArgsConstructor
public class TotalsPurchaseReturnImplDesign implements TotalDesignInterface<Total_Buy_Re> {

    private final DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> dataInterface;
    private final TotalBuyReturnService totalBuyReturnService;

    @Override
    public void getTable(TableView<Total_Buy_Re> tableView) {
        Callback<TableColumn.CellDataFeatures<Total_Buy_Re, String>, ObservableValue<String>> cellName = f -> f.getValue().getSuppliers().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<Total_Buy_Re, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, Setting_Language.WORD_TYPE, 3, colNameType);

        Callback<TableColumn.CellDataFeatures<Total_Buy_Re, String>, ObservableValue<String>> colStockName = f -> f.getValue().getStockData().nameProperty();
        addColumn(tableView, Setting_Language.WORD_STOCK, tableView.getColumns().size(), colStockName);

    }

    @Override
    public List<Total_Buy_Re> dataList() throws Exception {
        return totalBuyReturnService.getListByCurrentMonth();
    }

    @Override
    public @NotNull Class<? super Total_Buy_Re> classForColumn() {
        return Total_Buy_Re.class;
    }

    @NotNull
    @Override
    public TotalsDataInterface<Total_Buy_Re> totalsDataInterface() {
        return new TotalsBuyReturnData();
    }

    @Override
    public int deleteData(Total_Buy_Re totalBuyRe) throws DaoException {
        return totalBuyReturnService.deleteById(Math.toIntExact(totalBuyRe.getId()));
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return totalBuyReturnService.deleteMultiData(ids);
    }

    @NotNull
    @Override
    public Predicate<Total_Buy_Re> filterById(int code) {
        return totalBuyRe -> totalBuyRe.getSuppliers().getId() == code;
    }

    @NotNull
    @Override
    public Predicate<Total_Buy_Re> filterByName(String name) {
        return totalBuyRe -> totalBuyRe.getSuppliers().getName().equals(name);
    }

    @Override
    public Predicate<Total_Buy_Re> filterByDelegate(String name) {
        return null;
    }

    @NotNull
    @Override
    public WriteExcelInterface<Total_Buy_Re> writeExcelInterface(List<Total_Buy_Re> items) {
        return new WriteExcelInterface<>() {
            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE
                        , Setting_Language.WORD_DATE
                        , Setting_Language.WORD_NAME
                        , Setting_Language.WORD_TOTAL
                        , Setting_Language.WORD_DISCOUNT
                        , Setting_Language.WORD_TOTAL
                };
            }

            @NotNull
            @Override
            public Object[] dataRow(Total_Buy_Re totalBuy) {
                return new Object[]{totalBuy.getId()
                        , totalBuy.getDate()
                        , totalBuy.getSuppliers().getName()
                        , totalBuy.getTotal()
                        , totalBuy.getDiscount()
                        , totalBuy.getTotal() - totalBuy.getDiscount()
                };
            }

            @NotNull
            @Override
            public List<Total_Buy_Re> itemsList() {
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
