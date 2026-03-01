package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.function.Function;

public interface AccountData<T extends BaseAccount> {

    int getIdName(T t1);

    String getName(T t1);

    Function<T, Integer> getNameIdFromAccount();

    T getAccountByNum(int i) throws DaoException;

    T objectData(int num, String date, double paid, String notes, Integer invoice_id, Integer code_id, TreasuryModel treasuryModel);

    void updateTableView(TableView<T> tableView);

    default WriteExcelInterface<T> writeExcelInterface(List<T> items) {
        return null;
    }

    List<T> getAccountBetweenDate(String dateFrom, String dateTo) throws Exception;
}
