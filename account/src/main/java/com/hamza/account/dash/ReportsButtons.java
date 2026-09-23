package com.hamza.account.dash;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.convert_treasury.WalletFeeReportController;
import com.hamza.account.controller.employee.CommissionRunController;
import com.hamza.account.controller.employee.DelegatePerformanceController;
import com.hamza.account.controller.name_account.CustomerRfmController;
import com.hamza.account.controller.name_account.PartyAgeingController;
import com.hamza.account.controller.name_account.PartyTrendController;
import com.hamza.account.controller.reports.*;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.party.payment.PartyPaymentsService;
import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.report.ReportEntry;
import com.hamza.account.features.report.monthly.MonthlySide;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.OpenApplication;
import com.hamza.account.view.ItemReportsApplication;
import com.hamza.account.view.ReportTotalYearlyApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static com.hamza.account.view.ReportTotalYearlyApplication.yearlyReportName;

public class ReportsButtons extends LoadData {

    private final MainItems mainScreenData;

    public ReportsButtons(DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainScreenData) throws Exception {
        super(daoFactory, dataPublisher);
        this.mainScreenData = mainScreenData;
    }

    /**
     * The summary, in a tab - it opened a second window of the home tab's own screen. It asks
     * {@code reports.show.summary}, and each of its cards the key of the screen it summarises.
     */
    public ButtonWithPerm summaryReport() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_SUMMARY;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.summary.accounts.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                ModernDashboardApp summary = new ModernDashboardApp(ModernDashboardApp.roads(mainScreenData, tabPane));
                addTape(tabPane, summary.getPane(), textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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
                Pane pane = ProfitLossController.standard().pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
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

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.returns.reasons.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = ReturnReasonsController.standard().pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * What the items sold over a period, one screen since 2026-09-23 where there were two - the item movement
     * ranking and the daily item sales, each in a window of its own. It asks {@code reports.show.items}, as
     * both did, and the service asks it again on every read.
     *
     * @param preset the period it opens on, or null for today - which is what the sidebar's button passes
     */
    public ButtonWithPerm itemSales(StatementPeriod preset) {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_ITEMS;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.itemsales.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = ItemSalesController.standard(preset).pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /** The item sales on two dates - the summary's period, opened from its best sellers. */
    public ButtonWithPerm itemSalesBetween(LocalDate from, LocalDate to) {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.REPORTS_SHOW_ITEMS;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.itemsales.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = ItemSalesController.between(from, to).pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * Customers' and suppliers' payments, one screen since 2026-09-23. It opens for a reader who may read
     * either side - {@code reports.show.sales} for customers, {@code reports.show.purchase} for suppliers -
     * and offers only the sides that reader may read; the service asks the side's key again on every read.
     *
     * @param preferred the side it opens on when that side is offered, or null for the first one offered
     */
    public ButtonWithPerm partyPayments(PartyKind preferred) {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AuthorizationGuard.isGranted(PartyPaymentsService.permissionFor(PartyKind.CUSTOMER))
                        || AuthorizationGuard.isGranted(PartyPaymentsService.permissionFor(PartyKind.SUPPLIER))
                        ? AppPermissions.PUBLIC_ACCESS : PermissionKey.deny();
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.party.payments.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = PartyPaymentsController.standard(preferred).pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm reportsHub() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.PUBLIC_ACCESS;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.hub.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = new ReportsHubController(openers(tabPane)).pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    /**
     * What each card of the hub runs: the entry point the report has always had - the sidebar's
     * button, or the constructor the button on its own screen calls. {@code ReportsHubWiringTest}
     * fails the build when an entry has no line here.
     */
    private Map<ReportEntry, ReportsHubController.Opener> openers(TabPane tabPane) {
        Map<ReportEntry, ReportsHubController.Opener> openers = new EnumMap<>(ReportEntry.class);
        openers.put(ReportEntry.SUMMARY, run(summaryReport(), tabPane));
        openers.put(ReportEntry.PROFIT_LOSS, run(profitLossReport(), tabPane));
        openers.put(ReportEntry.YEARLY, () -> run(reportYearly(), tabPane).open());
        openers.put(ReportEntry.SALES_BY_YEAR, run(monthlyTotals(MonthlySide.SALES), tabPane));
        openers.put(ReportEntry.PURCHASES_BY_YEAR, run(monthlyTotals(MonthlySide.PURCHASES), tabPane));
        openers.put(ReportEntry.ITEMS_RANK, run(itemSales(StatementPeriod.THIS_MONTH), tabPane));
        openers.put(ReportEntry.ITEMS_DAILY, run(itemSales(StatementPeriod.TODAY), tabPane));
        openers.put(ReportEntry.RETURN_REASONS, run(returnReasonsReport(), tabPane));

        openers.put(ReportEntry.CUSTOMER_BALANCES, run(mainScreenData.getAccountButtonsCustom(), tabPane));
        openers.put(ReportEntry.CUSTOMER_AGEING, () -> new OpenApplication<>(
                new PartyAgeingController<>(daoFactory, dataPublisher, mainScreenData.getCustomData())));
        openers.put(ReportEntry.CUSTOMER_TREND, () -> new OpenApplication<>(
                new PartyTrendController<>(daoFactory, dataPublisher, mainScreenData.getCustomData())));
        openers.put(ReportEntry.CUSTOMER_RFM, () -> new OpenApplication<>(new CustomerRfmController()));
        openers.put(ReportEntry.CUSTOMER_PAYMENTS, run(partyPayments(PartyKind.CUSTOMER), tabPane));
        openers.put(ReportEntry.SUPPLIER_BALANCES, run(mainScreenData.getAccountButtonsSup(), tabPane));
        openers.put(ReportEntry.SUPPLIER_AGEING, () -> new OpenApplication<>(
                new PartyAgeingController<>(daoFactory, dataPublisher, mainScreenData.getSuppliersData())));
        openers.put(ReportEntry.SUPPLIER_TREND, () -> new OpenApplication<>(
                new PartyTrendController<>(daoFactory, dataPublisher, mainScreenData.getSuppliersData())));
        openers.put(ReportEntry.SUPPLIER_PAYMENTS, run(partyPayments(PartyKind.SUPPLIER), tabPane));

        // The item reports open over the list's filter from the items screen; from here there is no
        // list, so they open over the whole catalogue - which is that screen's own filter before
        // anybody has narrowed it.
        openers.put(ReportEntry.ITEM_REPORTS, () -> new ItemReportsApplication(ItemCatalogFilter.EMPTY).start(new Stage()));
        openers.put(ReportEntry.INVENTORY, () -> run(mainScreenData.getItemsButtons().inventory(), tabPane).open());

        openers.put(ReportEntry.CAPITAL, run(mainScreenData.getTreasuryButtons().treasuryCapital(), tabPane));
        openers.put(ReportEntry.WALLET_FEES, () -> new OpenApplication<>(new WalletFeeReportController()));
        openers.put(ReportEntry.EXPENSE_REPORTS, () -> mainScreenData.getTreasuryButtons().openExpenseReports(tabPane));

        openers.put(ReportEntry.DELEGATE_PERFORMANCE, () -> new OpenApplication<>(new DelegatePerformanceController()));
        openers.put(ReportEntry.COMMISSION_RUNS, () -> new OpenApplication<>(new CommissionRunController()));

        openers.put(ReportEntry.SHIFT_REPORTS, run(mainScreenData.getSettingButtons().adminShifts(), tabPane));
        return openers;
    }

    /** A sidebar button run the way {@code MenuButtonSetting} runs it: in a tab where it opens in one. */
    private static ReportsHubController.Opener run(ButtonWithPerm button, TabPane tabPane) {
        return () -> {
            if (button.showOnTapPane()) {
                button.actionAddPaneToTabPane(tabPane);
            } else {
                button.action();
            }
        };
    }

    /**
     * The monthly sales and purchases - one screen and one sidebar button since 2026-09-23, where there were
     * two entries, one class opened twice in windows of their own. The button opens for a reader who may read
     * either side; which sides the screen offers, and the service's own check, are the screen's.
     *
     * @param preferred the side it opens on when that side is offered, or null for the first one offered
     */
    public ButtonWithPerm monthlyTotals(MonthlySide preferred) {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AuthorizationGuard.isGranted(MonthlySide.SALES.permission())
                        || AuthorizationGuard.isGranted(MonthlySide.PURCHASES.permission())
                        ? AppPermissions.PUBLIC_ACCESS : PermissionKey.deny();
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("report.monthly.title");
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Pane pane = MonthlyTotalsController.standard(preferred).pane();
                addTape(tabPane, pane, textName(), AppIcon.REPORT.graphic(20));
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

}
