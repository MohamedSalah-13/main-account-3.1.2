package com.hamza.account.document;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;


public interface TotalsAndPurchaseList<T1 extends BasePurchasesAndSales, T2 extends BaseTotals> {

    DaoList<T2> totalDao();

    List<T2> totalList(String dateFrom, String dateTo) throws Exception;

    List<T1> purchaseOrSalesList(int from, int to) throws Exception;

    int getMaxId() throws Exception;

    /**
     * One page of a search, newest first, with the totals of everything it matched.
     * Both dates in {@code criteria} may be absent, which searches the whole history.
     */
    TotalsPage<T2> searchTotals(TotalsSearchCriteria criteria, int page, int pageSize) throws Exception;

    /** The money the same criteria add up to, asked for separately - it is the slow half. */
    TotalsSummaryRow summarizeTotals(TotalsSearchCriteria criteria) throws Exception;

}
