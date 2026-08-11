package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.name_account.AccountDetailsController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;

public class AccountDetailsApplication {

    public AccountDetailsApplication(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<?, ?, ?, ?> dataInterface
            , String name, int code_id) throws Exception {
        AccountDetailsController<?, ?> accountDetailsController = new AccountDetailsController<>(daoFactory, dataPublisher, dataInterface, name, code_id);
        new OpenApplication<>(accountDetailsController);

    }

}
