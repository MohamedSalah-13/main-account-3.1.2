package com.hamza.account.interfaces.impl_totalDesgin;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.interfaces.totals.TotalsBuyData;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;

@Log4j2
public record TotalsPurchaseImplDesign(DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> dataInterface,
                                       ServiceData serviceData) implements TotalDesignInterface<Total_buy> {

    @Override
    public void getTable(TableView<Total_buy> tableView) {
        Callback<TableColumn.CellDataFeatures<Total_buy, String>, ObservableValue<String>> cellName = f -> f.getValue().getSupplierData().nameProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 2, cellName);

        Callback<TableColumn.CellDataFeatures<Total_buy, String>, ObservableValue<String>> colNameType = f -> f.getValue().getInvoiceType().typeProperty();
        addColumn(tableView, Setting_Language.WORD_TYPE, 3, colNameType);

        Callback<TableColumn.CellDataFeatures<Total_buy, String>, ObservableValue<String>> colStockName = f -> f.getValue().getStockData().nameProperty();
        addColumn(tableView, Setting_Language.WORD_STOCK, tableView.getColumns().size(), colStockName);

    }

    @Override
    public List<Total_buy> dataList() throws Exception {
        return serviceData.getTotalBuyService().getListByCurrentMonth();
    }

    @NotNull
    @Override
    public Class<? super Total_buy> classForColumn() {
        return Total_buy.class;
    }


    @Override
    public TotalsDataInterface<Total_buy> totalsDataInterface() {
        return new TotalsBuyData();
    }

    @Override
    public int deleteData(Total_buy totalBuy) throws DaoException {
        return dataInterface.totalsAndPurchaseList().totalDao().deleteById(totalBuy.getId());
    }

    @Override
    public int deleteMultiData(@NotNull Integer... ids) throws Exception {
        return serviceData.getTotalBuyService().deleteMultiData(ids);
    }


    @Override
    public Predicate<Total_buy> filterById(int code) {
        return totalBuy -> totalBuy.getSupplierData().getId() == code;
    }


    @Override
    public Predicate<Total_buy> filterByName(String name) {
        return totalBuy -> totalBuy.getSupplierData().getName().equals(name);
    }

    @Override
    public Predicate<Total_buy> filterByDelegate(String name) {
        return null;
    }

    @Override
    public Predicate<Total_buy> filterByInvoiceType(InvoiceType type) {
        return totalBuy -> totalBuy.getInvoiceType().equals(type);
    }


    @Override
    public WriteExcelInterface<Total_buy> writeExcelInterface(List<Total_buy> items) {
        return new WriteExcelInterface<>() {

            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE, Setting_Language.WORD_DATE, Setting_Language.WORD_NAME, Setting_Language.WORD_TYPE, Setting_Language.WORD_TOTAL, Setting_Language.TOTAL_DISCOUNT, Setting_Language.THE_AMOUNT, Setting_Language.WORD_PAID, Setting_Language.WORD_REST};
            }


            @NotNull
            @Override
            public Object[] dataRow(Total_buy totalBuy) {
                return new Object[]{totalBuy.getId(), totalBuy.getDate(), totalBuy.supplierDataProperty().get().getName(), totalBuy.getInvoiceType().getType(), totalBuy.getTotal(), totalBuy.getDiscount(), totalBuy.getTotal_after_discount(), totalBuy.getPaid(), totalBuy.getRest()};
            }


            @NotNull
            @Override
            public List<Total_buy> itemsList() {
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
