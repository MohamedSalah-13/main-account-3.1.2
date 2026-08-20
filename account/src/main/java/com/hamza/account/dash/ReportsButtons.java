package com.hamza.account.dash;

import com.hamza.account.Main;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.reports.*;
import com.hamza.account.features.returns.JdbcReturnableRepository;
import com.hamza.account.features.returns.ReturnReasonReportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.OpenApplication;
import com.hamza.account.view.ReportTotalYearlyApplication;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.hamza.account.view.ReportTotalYearlyApplication.yearlyReportName;

public class ReportsButtons extends LoadData {

    private final MainItems mainScreenData;

    public ReportsButtons(DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainScreenData) throws Exception {
        super(daoFactory, dataPublisher);
        this.mainScreenData = mainScreenData;
    }

    public ButtonWithPerm summaryReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_SUMMARY;
            }

            @Override
            public void action() throws Exception {
//                new SummaryApplication(daoFactory, textName()).start(new Stage());
                new ModernDashboardApp(daoFactory, dataPublisher).showWindow();
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.summary.accounts.title");
            }
        };
    }

    public ButtonWithPerm reportYearly() throws Exception {

        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_PROFIT;
            }

            @Override
            public void action() throws Exception {
                var reportTotalYearlyApplication = new ReportTotalYearlyApplication(daoFactory);
                reportTotalYearlyApplication.start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return yearlyReportName();
            }

        };
    }

    public ButtonWithPerm profitLossReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_PROFIT;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.profit.loss.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new ProfitLossController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm returnReasonsReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_RETURNS;
            }

            @Override
            public void action() {
                var service = new ReturnReasonReportService(new JdbcReturnableRepository());
                DialogReturnReasonsReport.show(service);
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.returns.reasons.title");
            }
        };
    }

    public ButtonWithPerm detailsReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.DISABLE_BUTTON;
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.details.title");
            }

            @Override
            public void action() {
            }
        };
    }

    public ButtonWithPerm itemsReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_ITEMS;
            }

            @Override
            public void action() throws IOException {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/ItemSalesRankView.fxml"),
                LanguageManager.getInstance().getResourceBundle());
                Parent root = loader.load();

                ItemSalesRankController controller = loader.getController();
                controller.setDaoFactory(daoFactory); // تمرير اتصال قاعدة البيانات

                Scene scene = new SceneAll(root);
                StageManager.show(
                        "item-sales-rank",
                        scene,
                        this.textName()
                );
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.dashboard.item.sales.rank.stage.title");
            }


            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) {

            }
        };
    }

    public ButtonWithPerm itemsReportDaily() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_ITEMS;
            }

            @Override
            public void action() throws IOException {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/DailyItemSalesView.fxml"),
                LanguageManager.getInstance().getResourceBundle());
                Parent root = loader.load();

                DailyItemSalesController controller = loader.getController();
                controller.setDaoFactory(daoFactory);  // تمرير اتصال قاعدة البيانات

                StageManager.show(
                        "item-sales-daily",
                        new SceneAll(root),
                        this.textName()
                );
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.daily.item.sales.title");
            }


            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) {

            }
        };
    }

    public ButtonWithPerm reportCustomPaid() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_SALES;
            }

            @Override
            public void action() throws Exception {
                var pane = new OpenFxmlApplication(new ReportPaid<>(mainScreenData.getCustomData().accountData(), textName())).getPane();
                new OpenApplication<>(new AppSettingInterface() {
                    @Override
                    public Pane pane() throws Exception {
                        return pane;
                    }

                    @Override
                    public String title() {
                        return textName();
                    }

                    @Override
                    public boolean resize() {
                        return true;
                    }
                });
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.customer.payments.title");
            }
        };
    }

    public ButtonWithPerm reportSupplierPaid() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_PURCHASE;
            }

            @Override
            public void action() throws Exception {
                var pane = new OpenFxmlApplication(new ReportPaid<>(mainScreenData.getSuppliersData().accountData(), textName())).getPane();
                new OpenApplication<>(new AppSettingInterface() {
                    @Override
                    public Pane pane() throws Exception {
                        return pane;
                    }

                    @Override
                    public String title() {
                        return textName();
                    }

                    @Override
                    public boolean resize() {
                        return true;
                    }
                });
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.supplier.payments.title");
            }
        };
    }


}
