package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.name_account.AddNameController;
import com.hamza.account.controller.name_account.NameController;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.jetbrains.annotations.NotNull;

public class NameButtons<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> {

    public NameButtons(DaoFactory daoFactory, DataPublisher dataPublisher
            , DataInterface<T1, T2, T3, T4> dataInterface) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
    }

    public ButtonWithPerm namesData() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return dataInterface.permAccountAndNameInt().showNames();
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return dataInterface.designInterface().nameTextOfData();
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                NameController<T1, T2, T3, T4> nameController = new NameController<>(dataInterface, daoFactory, dataPublisher);
                Pane pane = new TableOpen<>(nameController).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().personCustomer);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm addName() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return dataInterface.permAccountAndNameInt().showNames();
            }

            @Override
            public void action() throws Exception {
                new AddForAllApplication(0, new AddNameController<>(dataInterface, daoFactory, dataPublisher, 0));
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD;
            }
        };
    }

}
