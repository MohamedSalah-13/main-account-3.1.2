package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.TableOpen;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.ExpensesDetailsApplication;
import com.hamza.account.view.OpenTreasuryApplication;
import com.hamza.account.view.OpenTreasuryCapitalApplication;
import com.hamza.account.view.OpenTreasuryCashApplication;
import com.hamza.account.view.OpenTreasuryTransferApplication;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.ProcessorApplication;
import com.hamza.controlsfx.language.LanguageManager;
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
                return OpenTreasuryDetailsApplication.accountStatementTitle();
            }

        };
    }


    /**
     * The treasury list itself - names, types, opening balances and what each one
     * holds now. Guarded by TREASURY_UPDATE rather than TREASURY_SHOW: the screen is
     * where a treasury is created and edited, and the read-only view of the same
     * numbers is the statement screen above.
     */
    public ButtonWithPerm treasuries() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_UPDATE;
            }

            @Override
            public void action() throws Exception {
                new OpenTreasuryApplication(daoFactory).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenTreasuryApplication.treasuriesTitle();
            }
        };
    }

    /** Moving money between two treasuries - its own permission, not TREASURY_UPDATE. */
    public ButtonWithPerm treasuryTransfer() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_TRANSFER;
            }

            @Override
            public void action() throws Exception {
                new OpenTreasuryTransferApplication(daoFactory).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenTreasuryTransferApplication.title();
            }
        };
    }

    /** Cash in and cash out by hand - the cashier's permission. */
    public ButtonWithPerm treasuryCash() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_DEPOSIT;
            }

            @Override
            public void action() throws Exception {
                new OpenTreasuryCashApplication(daoFactory).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenTreasuryCashApplication.title();
            }
        };
    }

    /**
     * The owner's own money in and out. Its own permission: an ordinary cashier has no
     * business reading how much the owner has drawn.
     */
    public ButtonWithPerm treasuryCapital() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.TREASURY_CAPITAL;
            }

            @Override
            public void action() throws Exception {
                new OpenTreasuryCapitalApplication(daoFactory).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenTreasuryCapitalApplication.title();
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
                return LanguageManager.getInstance().getString("common.process");
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
