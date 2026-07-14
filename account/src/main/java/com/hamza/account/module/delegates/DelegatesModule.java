package com.hamza.account.module.delegates;

import com.hamza.account.module.AppFeature;
import com.hamza.account.module.AppModule;
import com.hamza.account.module.ModuleContext;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class DelegatesModule implements AppModule {

    @Override
    public String name() {
        return "Delegates Module";
    }

    @Override
    public AppFeature feature() {
        return AppFeature.DELEGATES;
    }

    @Override
    public void registerMenus(ModuleContext context) {
        Menu delegatesMenu = new Menu("المندوبين");

        if (PermissionHelper.has(PermissionCode.DELEGATES_SHOW)) {
            MenuItem delegatesItem = new MenuItem("بيانات المندوبين");
            delegatesItem.setOnAction(event -> {
                try {
                    context.mainScreenController()
                            .getDelegatesButtons()
                            .delegates()
                            .actionAddPaneToTabPane(context.tabPane());
                } catch (Exception e) {
                    log.error("خطأ في فتح بيانات المندوبين", e);
                }
            });
            delegatesMenu.getItems().add(delegatesItem);
        }

        if (PermissionHelper.has(PermissionCode.TARGETS_SHOW)) {
            MenuItem targetsItem = new MenuItem("أهداف المندوبين");
            targetsItem.setOnAction(event -> {
                try {
                    context.mainScreenController()
                            .getDelegatesButtons()
                            .targets()
                            .actionAddPaneToTabPane(context.tabPane());
                } catch (Exception e) {
                    log.error("خطأ في فتح أهداف المندوبين", e);
                }
            });
            delegatesMenu.getItems().add(targetsItem);
        }

        if (PermissionHelper.has(PermissionCode.DELEGATES_REPORTS)) {
            MenuItem performanceReportItem = new MenuItem("تقرير أداء المندوبين");
            performanceReportItem.setOnAction(event -> {
                try {
                    context.mainScreenController()
                            .getDelegatesButtons()
                            .performanceReport()
                            .actionAddPaneToTabPane(context.tabPane());
                } catch (Exception e) {
                    log.error("خطأ في فتح تقرير أداء المندوبين", e);
                }
            });
            delegatesMenu.getItems().add(performanceReportItem);
        }

        if (PermissionHelper.has(PermissionCode.TARGETS_REPORTS)) {
            MenuItem targetReportItem = new MenuItem("تقرير تحقيق الأهداف");
            targetReportItem.setOnAction(event -> {
                try {
                    context.mainScreenController()
                            .getDelegatesButtons()
                            .targetReport()
                            .actionAddPaneToTabPane(context.tabPane());
                } catch (Exception e) {
                    log.error("خطأ في فتح تقرير تحقيق الأهداف", e);
                }
            });
            delegatesMenu.getItems().add(targetReportItem);
        }

        if (PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
            MenuItem commissionsItem = new MenuItem("عمولات المندوبين");
            commissionsItem.setOnAction(event -> {
                try {
                    context.mainScreenController()
                            .getDelegatesButtons()
                            .commissions()
                            .actionAddPaneToTabPane(context.tabPane());
                } catch (Exception e) {
                    log.error("خطأ في فتح عمولات المندوبين", e);
                }
            });
            delegatesMenu.getItems().add(commissionsItem);
        }

        if (!delegatesMenu.getItems().isEmpty()) {
            context.menuBar().getMenus().add(delegatesMenu);
        }
    }
}
