package com.hamza.account.controller.main;

import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.NameAndAccountInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.NameService;

public class LoadOtherData<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> extends LoadData {

    protected DataInterface<T1, T2, T3, T4> dataInterface;
    protected NameAndAccountInterface<T3, T4> nameAndAccountInterface;
    protected AccountData<T4> accountData;
    protected NameService<T3> nameService;
    protected NameData<T3> nameData;

    public LoadOtherData(DataInterface<T1, T2, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.dataInterface = dataInterface;
        this.nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        this.accountData = dataInterface.accountData();
        this.nameData = dataInterface.nameData();
        this.nameService = new NameService<>(this.nameData);
    }
}
