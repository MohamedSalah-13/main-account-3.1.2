package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.delegates.DelegateCommissionsController;
import com.hamza.account.controller.delegates.DelegatePerformanceReportController;
import com.hamza.account.controller.delegates.DelegateTargetReportController;
import com.hamza.account.controller.delegates.DelegateTargetsController;
import com.hamza.account.controller.delegates.DelegatesController;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.jetbrains.annotations.NotNull;

public class DelegatesButtons extends LoadData {

    public DelegatesButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm delegates() {
        return new ButtonWithPerm() {
            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "المندوبين";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new DelegatesController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().personCustomer);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm targets() {
        return new ButtonWithPerm() {
            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "أهداف المندوبين";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new DelegateTargetsController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm performanceReport() {
        return new ButtonWithPerm() {
            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "تقرير أداء المندوبين";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new DelegatePerformanceReportController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm targetReport() {
        return new ButtonWithPerm() {
            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "تقرير تحقيق الأهداف";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new DelegateTargetReportController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm commissions() {
        return new ButtonWithPerm() {
            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return "عمولات المندوبين";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new OpenFxmlApplication(new DelegateCommissionsController()).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().reports);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }
}