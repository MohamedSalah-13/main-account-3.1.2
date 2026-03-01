package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.name_account.AccountDetailsController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.others.CssToColorHelper;

public class AccountDetailsApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    public AccountDetailsApplication(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface
            , String name, int code_id) throws Exception {
        CssToColorHelper helper = new CssToColorHelper();
        AccountDetailsController<T1, T2, T3, T4> accountDetailsController = new AccountDetailsController<>(daoFactory, dataPublisher, dataInterface, name, code_id, helper);
        new OpenApplication<>(accountDetailsController);

    }

}
