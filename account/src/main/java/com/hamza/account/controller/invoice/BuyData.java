package com.hamza.account.controller.invoice;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.service.NameService;

public class BuyData<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    protected final InvoiceBuy<T1, T2, T3, T4> invoiceBuy;
    protected final DataInterface<T1, T2, T3, T4> dataInterface;
    protected final DesignInterface designInterface;
    protected final int num_invoice_update;
    protected final TotalsAndPurchaseList<T1, T2> totalsAndPurchaseList;
    protected final PurchaseSalesInterface purchaseSalesInterface;
    protected final NameData<T3> t3NameData;
    protected final AccountData<T4> accountData;
    protected final NameService<T3> nameService;
    protected final NameAndAccountInterface<T3, T4> nameAndAccountInterface;
//    protected int numItem;

    public BuyData(DataInterface<T1, T2, T3, T4> dataInterface
            , DataPublisher dataPublisher
            , int numInvoiceUpdate) throws Exception {
        this.dataInterface = dataInterface;
        this.num_invoice_update = numInvoiceUpdate;
        this.designInterface = dataInterface.designInterface();
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.t3NameData = dataInterface.nameData();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.purchaseSalesInterface = dataInterface.purchaseSalesInterface();
        this.nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        this.accountData = dataInterface.accountData();
        this.nameService = new NameService<>(t3NameData);

    }

}
