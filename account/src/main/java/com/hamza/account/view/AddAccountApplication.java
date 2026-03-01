package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.name_account.Add_AccountController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;

public class AddAccountApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    public AddAccountApplication(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface
            , int code_id, int num, String name) throws Exception {
        Add_AccountController<T1, T2, T3, T4> addAccountController = new Add_AccountController<>(daoFactory, dataPublisher, dataInterface, code_id, num, name);
        new AddForAllApplication(num, addAccountController);
    }
}
