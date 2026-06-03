package com.hamza.account.interfaces.api;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.event.EventType;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.database.DaoException;

import java.util.List;

/**
 * DataInterface defines the contract for the data handling components that interact with
 * design interface, DAO factory, and various entities such as purchase, sales, customers,
 * and accounts.
 *
 * @param <T1> the type representing purchase or sales data
 * @param <T2> the type representing total data of purchase or sales
 * @param <T3> the type representing customer or supplier names
 * @param <T4> the type representing customer or supplier accounts
 */
public interface DataInterface<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    DesignInterface designInterface();

    TotalDesignInterface<T2> totalDesignInterface();

//    Publisher<String> publisherPurchaseOrSales();

    List<T1> listForAllPurchase(int id) throws DaoException;

    InvoiceBuy<T1, T2, T3, T4> invoiceBuy();

    NameData<T3> nameData();

    TotalsAndPurchaseList<T1, T2> totalsAndPurchaseList();

    NameAndAccountInterface<T3, T4> nameAndAccountInterface() throws Exception;

    AccountData<T4> accountData();

    default PurchaseSalesInterface purchaseSalesInterface() {
        return new PurchaseSalesInterface() {
        };
    }

    void addList(List<T2> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException;

    EventType getEventType();
}
