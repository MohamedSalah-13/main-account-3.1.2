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
import com.hamza.controlsfx.observer.Publisher;

public class TotalsService<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> {

    protected TotalDesignInterface<T2> totalDesignInterface;
    protected TotalsAndPurchaseList<T1, T2> totalsAndPurchaseList;
    protected TotalsDataInterface<T2> totalsDataInterface;
    protected Publisher<String> stringPublisher;

    public TotalsService(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.totalDesignInterface = dataInterface.totalDesignInterface();
        this.stringPublisher = dataInterface.publisherPurchaseOrSales();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.totalsDataInterface = totalDesignInterface.totalsDataInterface();
    }

}
