package com.hamza.account.service;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;

/**
 * What a totals screen needs beyond {@link LoadOtherData}. It names the party and account
 * types only - a totals row is a {@code BaseTotals} here, and the per-family questions
 * are answered by {@link TotalsDataInterface}.
 *
 * <p>The second handle on the same interface this used to keep - {@code totalsInterface},
 * which existed because {@code LoadOtherData}'s own said nothing about the totals type -
 * is gone: the two are now the same type, so the inherited {@code dataInterface} is it.
 */
public class TotalsService<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> {

    protected TotalDesignInterface totalDesignInterface;
    protected TotalsAndPurchaseList<?, ?> totalsAndPurchaseList;
    protected TotalsDataInterface totalsDataInterface;

    public TotalsService(DataInterface<?, ?, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.totalDesignInterface = dataInterface.totalDesignInterface();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.totalsDataInterface = totalDesignInterface.totalsDataInterface();
    }

}
