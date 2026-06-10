package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.name_account.AccountController2;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.others.CssToColorHelper;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.jetbrains.annotations.NotNull;


public class AccountButtons<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements ButtonWithPerm {

    public AccountButtons(DaoFactory daoFactory
            , DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
    }


    @Override
    public void action() throws Exception {
    }

    @NotNull
    @Override
    public String textName() {
        return dataInterface.designInterface().nameTextOfAccount();
    }

    @Override
    public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
        CssToColorHelper helper = new CssToColorHelper();
        AccountController2<T1, T2, T3, T4> accountDetailsController = new AccountController2<>(daoFactory, dataPublisher, dataInterface);
        Pane pane = new OpenFxmlApplication(accountDetailsController).getPane();
        pane.getChildren().add(helper);
        pane.getStylesheets().add(dataInterface.designInterface().styleSheet());
        addTape(tabPane, pane, textName(), new Image_Setting().account);
    }

    @Override
    public boolean showOnTapPane() {
        return true;
    }

}

