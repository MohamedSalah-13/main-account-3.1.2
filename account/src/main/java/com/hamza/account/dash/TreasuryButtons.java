package com.hamza.account.dash;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.expense.ExpensesController;
import com.hamza.account.controller.others.ProcessesController;
import com.hamza.account.controller.convert_treasury.TreasureDetailsController;
import com.hamza.account.controller.convert_treasury.TreasuryCapitalController;
import com.hamza.account.controller.convert_treasury.TreasuryCashController;
import com.hamza.account.controller.convert_treasury.TreasuryController;
import com.hamza.account.controller.convert_treasury.TreasuryTransferController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.OpenExpensesApplication;
import com.hamza.account.view.OpenTreasuryApplication;
import com.hamza.account.view.OpenTreasuryCapitalApplication;
import com.hamza.account.view.OpenTreasuryCashApplication;
import com.hamza.account.view.OpenTreasuryTransferApplication;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.ProcessorApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new TreasureDetailsController(daoFactory, dataPublisher)).getPane();
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_CASH.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new TreasuryController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_BANK.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new TreasuryTransferController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_WALLET.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new TreasuryCashController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_CASH.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new TreasuryCapitalController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_WALLET.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm openProcess() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.AUDIT_VIEW;
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new ProcessesController()).getPane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * The expenses list. It opened on {@code treasury.show} while {@code expenses.show} existed and
     * nothing read it (docs/expenses-plan.md ع-٥); the permission that carries the name is the one
     * asked now, and V64 grants it to whoever held the other.
     */
    public ButtonWithPerm openExpenses() {
        return new ButtonWithPerm() {

            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.EXPENSES_SHOW;
            }

            @Override
            public void action() throws Exception {
                new OpenExpensesApplication(daoFactory, dataPublisher).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return OpenExpensesApplication.title();
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                ExpensesController controller = new ExpensesController(daoFactory, dataPublisher);
                Pane pane = new OpenFxmlApplication(controller).getPane();
                // Kept on the node so the reports hub can reach the list in the tab it opens or
                // finds already open (openExpenseReports).
                pane.getProperties().put(ExpensesController.class, controller);
                addTape(tabPane, pane, textName(), AppIcon.TREASURY_CASH.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * The expense reports as the reports hub opens them: the list's tab first - a new one, or the one
     * already open - and then that list's own reports button, so a line in a report opens the list on
     * it exactly as it does from the list. Opening the reports with no list behind them would leave
     * every line of them pointing nowhere.
     */
    public void openExpenseReports(TabPane tabPane) throws Exception {
        openExpenses().actionAddPaneToTabPane(tabPane);
        Tab shown = tabPane.getSelectionModel().getSelectedItem();
        if (shown != null && shown.getContent() != null
                && shown.getContent().getProperties().get(ExpensesController.class) instanceof ExpensesController list) {
            // Queued behind the list's own first load, which sets its period (this month) in a
            // runLater of its own: asked straight away, the reports were read over the list's
            // unset filter - all of history - while the list beside them showed this month.
            Platform.runLater(list::openReports);
        }
    }

}
