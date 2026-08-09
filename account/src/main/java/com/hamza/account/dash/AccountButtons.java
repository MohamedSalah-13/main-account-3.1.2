package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.AccountTotalsApplication;
import javafx.scene.control.TabPane;
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
        AccountTotalsApplication<T1, T2, T3, T4> design = new AccountTotalsApplication<>(daoFactory, dataPublisher, dataInterface);
        addTape(tabPane, design.getPane(), textName(), new Image_Setting().account);
    }

    @Override
    public boolean showOnTapPane() {
        return true;
    }

    @Override
    public UserPermissionType getPermissionType() {
        return dataInterface.permAccountAndNameInt().showAccounts();
    }
}

