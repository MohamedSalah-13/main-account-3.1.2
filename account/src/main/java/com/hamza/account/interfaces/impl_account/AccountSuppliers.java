package com.hamza.account.interfaces.impl_account;

import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

public record AccountSuppliers(DaoFactory daoFactory) implements AccountData<SupplierAccount> {

    @Override
    public int getIdName(SupplierAccount supplierAccount) {
        return supplierAccount.getSuppliers().getId();
    }

    @Override
    public String getName(SupplierAccount supplierAccount) {
        return supplierAccount.getSuppliers().getName();
    }

    @Override
    public Function<SupplierAccount, Integer> getNameIdFromAccount() {
        return supplierAccount -> supplierAccount.getSuppliers().getId();
    }

    @Override
    public SupplierAccount getAccountByNum(int i) throws DaoException {
        return daoFactory.suppliersAccountDao().getAccountByNum(i);
    }

    @Override
    public SupplierAccount objectData(int num, String date, double paid, String notes, Integer invoice_id, Integer code_id, TreasuryModel treasuryModel) {
        return new SupplierAccount(num, date, paid, notes, invoice_id, new Suppliers(code_id), treasuryModel);
    }

    @Override
    public void updateTableView(TableView<SupplierAccount> tableView) {
        // add column code
        Callback<TableColumn.CellDataFeatures<SupplierAccount, String>, ObservableValue<String>> callback = f -> f.getValue().getSuppliers().idProperty().asString();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_CODE, 0, callback);

        // add column name
        Callback<TableColumn.CellDataFeatures<SupplierAccount, String>, ObservableValue<String>> callback_name = f -> f.getValue().getSuppliers().nameProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 1, callback_name);
    }

    @Override
    public WriteExcelInterface<SupplierAccount> writeExcelInterface(List<SupplierAccount> items) {
        return new WriteExcelInterface<>() {

            @NotNull
            @Override
            public Object[] columnHeader() {
                return new Object[]{Setting_Language.WORD_CODE
                        , Setting_Language.WORD_NAME
                        , Setting_Language.WORD_PUR
                        , Setting_Language.WORD_PAID
                        , Setting_Language.THE_AMOUNT
                        , Setting_Language.WORD_DATE
                };
            }


            @NotNull
            @Override
            public Object[] dataRow(SupplierAccount supplierAccount) {
                return new Object[]{supplierAccount.getSuppliers().getId()
                        , supplierAccount.getSuppliers().getName()
                        , supplierAccount.getPurchase()
                        , supplierAccount.getPaid()
                        , supplierAccount.getAmount()
                        , supplierAccount.getDate()
                };
            }


            @NotNull
            @Override
            public List<SupplierAccount> itemsList() {
                return items;
            }

            @Override
            public boolean addDataToFile() {
                return true;
            }


            @NotNull
            @Override
            public String sheetName() {
                return Setting_Language.WORD_CUSTOM_ACC;
            }
        };

    }

    @Override
    public List<SupplierAccount> getAccountBetweenDate(String dateFrom, String dateTo) throws Exception {
        return daoFactory.suppliersAccountDao().getAccountBetweenDate(dateFrom, dateTo);
    }

}
