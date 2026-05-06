package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.ProfitLossController;
import com.hamza.account.controller.reports.ReportItemsController;
import com.hamza.account.controller.reports.ReportPaid;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.OpenApplication;
import com.hamza.account.view.ReportTotalYearlyApplication;
import com.hamza.account.view.TargetApplication;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import static com.hamza.account.view.ReportTotalYearlyApplication.YEARLY_REPORT_NAME;

public class ReportsButtons extends LoadData {

    private final MainItems mainScreenData;

    public ReportsButtons(DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainScreenData) throws Exception {
        super(daoFactory, dataPublisher);
        this.mainScreenData = mainScreenData;
    }

    public ButtonWithPerm summaryReport() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_SUMMARY;
            }

            @Override
            public void action() throws Exception {
//                new SummaryApplication(daoFactory, textName()).start(new Stage());
                new ModernDashboardApp(daoFactory).showWindow();
            }

            @NotNull
            @Override
            public String textName() {
                return "ملخص الحسابات";
            }
        };
    }

    public ButtonWithPerm delegateReport() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @Override
            public void action() throws Exception {
                new TargetApplication(daoFactory, dataPublisher, textName()).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.REPORT_DELEGATE;
            }

        };
    }

    public ButtonWithPerm reportYearly() throws Exception {

        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_PROFIT;
            }

            @Override
            public void action() throws Exception {
                var reportTotalYearlyApplication = new ReportTotalYearlyApplication(daoFactory);
                reportTotalYearlyApplication.start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return YEARLY_REPORT_NAME;
            }

        };
    }

    public ButtonWithPerm profitLossReport() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_PROFIT;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_PROFIT_LOSS;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new ProfitLossController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm detailsReport() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.DISABLE_BUTTON;
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.DETAILS;
            }

            @Override
            public void action() {
            }
        };
    }

    public ButtonWithPerm itemsReport() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_ITEMS;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_REPORT_ITEMS;
            }


            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                var pane = new OpenFxmlApplication(new ReportItemsController(daoFactory)).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm reportCustomPaid() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_SALES;
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
                return "مدفوعات العملاء";
            }
        };
    }

    public ButtonWithPerm reportSupplierPaid() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.REPORTS_SHOW_PURCHASE;
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
                return "مدفوعات الموردين";
            }
        };
    }


}
