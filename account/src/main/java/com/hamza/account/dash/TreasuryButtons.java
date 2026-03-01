package com.hamza.account.dash;

import com.hamza.account.controller.convert_treasury.AddDepositController;
import com.hamza.account.controller.convert_treasury.ConvertTreasuryController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.ExpensesDetailsApplication;
import com.hamza.account.view.OpenApplicationWithData;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.ProcessorApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class TreasuryButtons extends ServiceData {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public TreasuryButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    public ButtonWithPerm addDeposit() {
        return new ButtonWithPerm() {
            public final String TRANSACTION_TYPE_DEPOSIT_WITHDRAWAL = "إيداع - صرف";

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @Override
            public void action() throws Exception {
                var controller = new AddDepositController(daoFactory, dataPublisher);
                OpenFxmlApplication application = new OpenFxmlApplication(controller);
                new OpenApplicationWithData<>(controller.getToolbarAccountActionInterface()
                        , controller.createTable()
                        , application.getPane(), textName());
            }

            @NotNull
            @Override
            public String textName() {
                return TRANSACTION_TYPE_DEPOSIT_WITHDRAWAL;
            }

        };
    }

    public ButtonWithPerm treasuryDetails() {
        return new ButtonWithPerm() {

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.TREASURY_SHOW;
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

    public ButtonWithPerm convertTreasury() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @Override
            public void action() throws Exception {
                ConvertTreasuryController itemsController = new ConvertTreasuryController(daoFactory);
                new TableOpen<>(itemsController).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.TREASURY_TRANSFERS;
            }
        };
    }

    public ButtonWithPerm openProcess() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new ProcessorApplication(daoFactory).start(new Stage());
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
            final ExpensesDetailsApplication expensesController = new ExpensesDetailsApplication(daoFactory, dataPublisher);

            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.TREASURY_SHOW;
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
