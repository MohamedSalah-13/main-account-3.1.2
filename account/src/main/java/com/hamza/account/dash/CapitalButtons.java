package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.controller.capital.CapitalManagementController;
import com.hamza.account.controller.capital.ProfitLossDistributionController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class CapitalButtons {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;

    public CapitalButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    /**
     * زر إدارة رأس المال والشركاء
     */
    public ButtonWithPerm capitalManagement() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                FXMLLoader loader = new FXMLLoader(
                        Main.class.getResource("view/capital/capital-management.fxml")
                );
                Parent root = loader.load();

                CapitalManagementController controller = loader.getController();

                Scene scene = new SceneAll(root);
                StageManager.show(
                        "capital-management",
                        scene,
                        textName()
                );
            }

            @NotNull
            @Override
            public String textName() {
                return "إدارة رأس المال";
            }

        };
    }

    /**
     * زر توزيع الأرباح والخسائر
     */
    public ButtonWithPerm profitLossDistribution() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                FXMLLoader loader = new FXMLLoader(
                        Main.class.getResource("view/capital/profit-loss-distribution.fxml")
                );
                Parent root = loader.load();

                ProfitLossDistributionController controller = loader.getController();
                controller.setDaoFactory(daoFactory);
                Scene scene = new SceneAll(root);
                StageManager.show(
                        "profit-loss-distribution",
                        scene,
                        textName()
                );
            }

            @NotNull
            @Override
            public String textName() {
                return "توزيع الأرباح والخسائر";
            }

        };
    }

    /**
     * زر تقارير رأس المال
     */
    public ButtonWithPerm capitalReports() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                log.info("فتح تقارير رأس المال");
                // TODO: Implement capital reports view
            }

            @NotNull
            @Override
            public String textName() {
                return "تقارير رأس المال";
            }
        };
    }
}
