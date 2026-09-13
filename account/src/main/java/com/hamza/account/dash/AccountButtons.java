package com.hamza.account.dash;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.AccountTotalsApplication;
import javafx.scene.control.TabPane;
import org.jetbrains.annotations.NotNull;


public class AccountButtons<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements ButtonWithPerm {

    private final AppIcon tabIcon;

    public AccountButtons(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<?, ?, T3, T4> dataInterface, AppIcon tabIcon) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.tabIcon = tabIcon;
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
        AccountTotalsApplication design = new AccountTotalsApplication(daoFactory, dataPublisher, dataInterface);
        addTape(tabPane, design.getPane(), textName(), tabIcon.graphic(20));
    }

    @Override
    public boolean showOnTapPane() {
        return true;
    }

    @Override
    public PermissionKey getPermissionType() {
        return dataInterface.permAccountAndNameInt().showAccounts();
    }
}

