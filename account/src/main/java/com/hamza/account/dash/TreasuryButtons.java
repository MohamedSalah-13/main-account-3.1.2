package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.controller.convert_treasury.TreasuryTransferController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.TableOpen;
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

    public ButtonWithPerm treasuryDetails() {
        return new ButtonWithPerm() {

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
            public void action() throws Exception {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/treasury/TreasuryTransfer.fxml"));
                Parent root = loader.load();
                TreasuryTransferController controller = loader.getController();
                controller.setDaoFactory(daoFactory); // تمرير اتصال قاعدة البيانات

                Scene scene = new SceneAll(root);
                StageManager.show(
                        "treasury-transfer",
                        scene,
                        "تحويلات الخزينة"
                );
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
