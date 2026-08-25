package com.hamza.account.controller.invoice;

import com.hamza.account.interfaces.api.*;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.service.NameService;

/**
 * The invoice screen's collaborators. It names the party and account types only: what a
 * document line and a document header concretely are is the implementation's business,
 * held here through wildcards and never correlated - see
 * {@link com.hamza.account.interfaces.api.InvoiceHeaderView} for the one place that used
 * to need the correlation.
 */
public class BuyData<T3 extends BaseNames, T4 extends BaseAccount> {

    protected final InvoiceBuy<?, ?, T3, T4> invoiceBuy;
    protected final DataInterface<?, ?, T3, T4> dataInterface;
    protected final DesignInterface designInterface;
    protected final int num_invoice_update;
    protected final TotalsAndPurchaseList<?, ?> totalsAndPurchaseList;
    protected final NameData<T3> t3NameData;
    protected final AccountData<T4> accountData;
    protected final NameService<T3> nameService;
    protected final NameAndAccountInterface<T3, T4> nameAndAccountInterface;
//    protected int numItem;

    public BuyData(DataInterface<?, ?, T3, T4> dataInterface
            , int numInvoiceUpdate) throws Exception {
        this.dataInterface = dataInterface;
        this.num_invoice_update = numInvoiceUpdate;
        this.designInterface = dataInterface.designInterface();
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.t3NameData = dataInterface.nameData();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        this.accountData = dataInterface.accountData();
        this.nameService = new NameService<>(t3NameData);

    }

}
