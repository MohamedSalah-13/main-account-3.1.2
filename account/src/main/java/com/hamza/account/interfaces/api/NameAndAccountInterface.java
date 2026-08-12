package com.hamza.account.interfaces.api;

import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.controlsfx.database.DaoList;

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

    /**
     * Which side this implementation speaks for, and the value carried by the
     * {@link com.hamza.account.features.events.NameChanged} and
     * {@link com.hamza.account.features.events.AccountChanged} events it fires -
     * it replaced the two publishers this used to hand out.
     */
    PartyKind partyKind();

    T1 getNameById(int id) throws Exception;

    List<T1> getFilterItems(String filter) throws Exception;

    List<T1> getCustomers(int rowsPerPage, int offset) throws Exception;

    int getCountItems();
}
