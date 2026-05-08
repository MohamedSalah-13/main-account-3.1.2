package com.hamza.account.interfaces.api;

import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;

/**
 * @param <T1> for customers or suppliers
 * @param <T2> for Account ( customers or suppliers )
 */
public interface NameAndAccountInterface<T1 extends BaseNames, T2 extends BaseAccount> {

    DaoList<T1> nameDao();

    List<T1> nameList() throws Exception;

    DaoList<T2> accountDao();

    List<T2> accountList() throws Exception;

    List<T2> accountListById(int id) throws Exception;

    List<T2> accountTotalList(String dateFrom, String dateTo);

    SearchInterface<T1> searchInterface();

    Publisher<String> addAccountPublisher();

    Publisher<String> addNamePublisher();

    T1 getNameById(int id) throws Exception;

    //TODO 5/8/2026 6:59 PM Mohamed:  add in searchInterface
    List<T1> getFilterItems(String filter) throws Exception;
    List<T1> getCustomers(int rowsPerPage, int offset) throws Exception;
    int getCountItems();
}
