package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.name_account.Add_AccountController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;

public class AddAccountApplication {

    public AddAccountApplication(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<?, ?, ?, ?> dataInterface
            , int code_id, int num, String name) throws Exception {
        Add_AccountController<?, ?> addAccountController = new Add_AccountController<>(daoFactory, dataPublisher, dataInterface, code_id, num, name);
        new AddForAllApplication(num, addAccountController);
    }
}
