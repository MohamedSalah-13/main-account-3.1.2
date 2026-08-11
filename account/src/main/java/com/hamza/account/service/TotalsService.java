package com.hamza.account.service;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.api.TotalsAndPurchaseList;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;

public class TotalsService<T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> {

    /**
     * The same interface the base class holds, with the totals type still visible.
     * {@link LoadOtherData} is shared with the name and account screens, which have no
     * totals type to name, so its own handle says nothing about T2.
     */
    protected final DataInterface<?, T2, T3, T4> totalsInterface;
    protected TotalDesignInterface<T2> totalDesignInterface;
    protected TotalsAndPurchaseList<?, T2> totalsAndPurchaseList;
    protected TotalsDataInterface<T2> totalsDataInterface;

    public TotalsService(DataInterface<?, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.totalsInterface = dataInterface;
        this.totalDesignInterface = dataInterface.totalDesignInterface();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.totalsDataInterface = totalDesignInterface.totalsDataInterface();
    }

}
