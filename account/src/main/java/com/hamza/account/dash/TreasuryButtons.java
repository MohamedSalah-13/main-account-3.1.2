package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.controller.convert_treasury.AddDepositController;
import com.hamza.account.controller.convert_treasury.TreasuryTransferController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.*;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class TreasuryButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public TreasuryButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    public ButtonWithPerm addDeposit() {
        return new ButtonWithPerm() {
            public final String TRANSACTION_TYPE_DEPOSIT_WITHDRAWAL = "إيداع - صرف";

            @Override
            public UserPermissionType getPermissionType() {
                return null;
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
                return null;
            }

            @Override
            public void action() throws Exception {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/treasury/TreasuryTransfer.fxml"));
                Parent root = loader.load();
                TreasuryTransferController controller = loader.getController();
                controller.setDaoFactory(daoFactory); // تمرير اتصال قاعدة البيانات

                Stage stage = new Stage();
                Scene scene = new SceneAll(root);
                stage.setScene(scene);
                stage.setTitle("تحويلات الخزينة");
                stage.show();
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
            final ExpensesDetailsApplication expensesController = new ExpensesDetailsApplication(dataPublisher);

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
