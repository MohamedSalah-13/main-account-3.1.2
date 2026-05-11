package com.hamza.account.interfaces.impl_account;

import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Treasury;
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

public record AccountCustomer(DaoFactory daoFactory) implements AccountData<CustomerAccount> {

    @Override
    public int getIdName(CustomerAccount customerAccount) {
        return customerAccount.getCustomers().getId();
    }

    @Override
    public String getName(CustomerAccount customerAccount) {
        return customerAccount.getCustomers().getName();
    }

    @Override
    public Function<CustomerAccount, Integer> getNameIdFromAccount() {
        return customerAccount -> customerAccount.getCustomers().getId();
    }

    @Override
    public CustomerAccount getAccountByNum(int i) throws DaoException {
        return daoFactory.customerAccountDao().getAccountByNumForUpdate(i);
    }

    @Override
    public CustomerAccount objectData(int num, String date, double paid, String notes, Integer invoice_id, Integer code_id, Treasury treasury) {
        return new CustomerAccount(num, date, paid, notes, invoice_id, new Customers(code_id), treasury);
    }

    @Override
    public void updateTableView(TableView<CustomerAccount> tableView) {
        // add column code
        Callback<TableColumn.CellDataFeatures<CustomerAccount, String>, ObservableValue<String>> callback = f -> f.getValue().getCustomers().idProperty().asString();
        ColumnSetting.addColumn(tableView, "كود العميل", 0, callback);

        // add column name
        Callback<TableColumn.CellDataFeatures<CustomerAccount, String>, ObservableValue<String>> callback_name = f -> f.getValue().getCustomers().nameProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 1, callback_name);
    }

    @Override
    public WriteExcelInterface<CustomerAccount> writeExcelInterface(List<CustomerAccount> items) {
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
            public Object[] dataRow(CustomerAccount customerAccount) {
                return new Object[]{customerAccount.getCustomers().getId()
                        , customerAccount.getCustomers().getName()
                        , customerAccount.getPurchase()
                        , customerAccount.getPaid()
                        , customerAccount.getAmount()
                        , customerAccount.getDate()
                };
            }


            @NotNull
            @Override
            public List<CustomerAccount> itemsList() {
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
    public List<CustomerAccount> getAccountBetweenDate(String dateFrom, String dateTo) throws Exception {
        return daoFactory.customerAccountDao().getAccountBetweenDate(dateFrom, dateTo);
    }
}
