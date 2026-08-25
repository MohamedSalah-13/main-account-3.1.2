package com.hamza.account.interfaces.api;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.document.InvoiceBuy;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveResult;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;

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

    TotalDesignInterface totalDesignInterface();

    /**
     * Which side an {@link com.hamza.account.features.events.InvoiceSaved} from
     * this implementation carries - it replaced the publisher this used to return.
     */
    InvoiceSide invoiceSide();

    List<T1> listForAllPurchase(int id) throws DaoException;

    InvoiceBuy<T1, T2, T3, T4> invoiceBuy();

    NameData<T3> nameData();

    TotalsAndPurchaseList<T1, T2> totalsAndPurchaseList();

    NameAndAccountInterface<T3, T4> nameAndAccountInterface() throws Exception;

    AccountData<T4> accountData();

    default PermAccountAndNameInt permAccountAndNameInt() {
        return PermAccountAndNameInt.forParty(designInterface().documentType().partyKind());
    }

    /**
     * Saves one document. The save pipeline stays generic, but it is built - and kept -
     * inside the implementation, where T1/T2 are still concrete classes; a screen only
     * ever sees the plain command and result. That is what lets the invoice screen stop
     * naming the two type parameters without a single unchecked cast.
     */
    InvoiceSaveResult saveInvoice(InvoiceSaveCommand command) throws DaoException;

    /** The header of a saved document, resolved - see {@link InvoiceHeaderView}. */
    default InvoiceHeaderView loadInvoiceHeader(int invoiceNumber) throws DaoException {
        T2 totals = totalsAndPurchaseList().totalDao().getDataById(invoiceNumber);
        TotalsDataInterface totalsData = totalDesignInterface().totalsDataInterface();
        return new InvoiceHeaderView(totals,
                totalsData.getNameData(totals),
                totalsData.getDelegateData(totals).getName(),
                totalsData.getIdData(totals),
                totalsData.getSourceInvoiceNumber(totals),
                totalsData.getReturnReason(totals),
                totalsData.getDateInsert(totals));
    }

    void addList(List<? extends BaseTotals> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException;
}
