package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.TableOpen;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.ExpensesDetailsApplication;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.ProcessorApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class TreasuryButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public TreasuryButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    public ButtonWithPerm treasuryDetails() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_SHOW;
            }

            @Override
            public void action() throws Exception {
                new OpenTreasuryDetailsApplication(daoFactory, dataPublisher).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenTreasuryDetailsApplication.ACCOUNT_STATEMENT_TITLE;
            }

        };
    }


    public ButtonWithPerm openProcess() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new ProcessorApplication().start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.PROCESS;
            }
        };
    }

    public ButtonWithPerm openExpenses() {
        return new ButtonWithPerm() {
            final ExpensesDetailsApplication expensesController = new ExpensesDetailsApplication();

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_SHOW;
            }

            @Override
            public void action() throws Exception {
                new TableOpen<>(expensesController).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return expensesController.titleName();
            }
        };
    }

}
