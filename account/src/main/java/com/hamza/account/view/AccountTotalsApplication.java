package com.hamza.account.view;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.name_account.AccountController2;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.layout.Pane;
import lombok.Getter;

@Getter
public class AccountTotalsApplication {

    private final Pane pane;

    public AccountTotalsApplication(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<?, ?, ?, ?> dataInterface) throws Exception {
        CssToColorHelper helper = new CssToColorHelper();
        AccountController2<?, ?> accountDetailsController = new AccountController2<>(daoFactory, dataPublisher, dataInterface);
        pane = new OpenFxmlApplication(accountDetailsController).getPane();
        pane.getChildren().add(helper);
        pane.getStylesheets().add(dataInterface.designInterface().styleSheet());
    }

}
