package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;


public interface TotalsAndPurchaseList<T1 extends BasePurchasesAndSales, T2 extends BaseTotals> {

    DaoList<T2> totalDao();

    List<T2> totalList(String dateFrom, String dateTo) throws Exception;

    List<T1> purchaseOrSalesList(int from, int to) throws Exception;

}
