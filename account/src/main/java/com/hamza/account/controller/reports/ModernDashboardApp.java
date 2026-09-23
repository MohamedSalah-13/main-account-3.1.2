package com.hamza.account.controller.reports;

import com.hamza.account.Main;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.controller.others.BaseCurrencySymbol;
import com.hamza.account.features.notification.StockLevel;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.summary.CashFlow;
import com.hamza.account.features.report.summary.LowStock;
import com.hamza.account.features.report.summary.LowStockItem;
import com.hamza.account.features.report.summary.Receivables;
import com.hamza.account.features.report.summary.Summary;
import com.hamza.account.features.report.summary.SummaryCard;
import com.hamza.account.features.report.summary.SummaryPaper;
import com.hamza.account.features.report.summary.SummaryPeriod;
import com.hamza.account.features.report.summary.SummaryService;
import com.hamza.account.features.report.summary.TrendPoint;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.table.FigureLine;
import com.hamza.account.table.ListToolbar;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TrendChart;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The summary («ملخص الحسابات»): the home tab's overview and the sidebar's report of the same name - a
 * period, the cards, the fourteen days' trend, the cash in and out, and the short lists an owner acts on.
 *
 * <p>The figures are {@code features/report/summary}'s, and none of them is defined here. The sales and the
 * purchases are net as the profit and loss says, the discounts the sales' own, the week starts on Saturday,
 * the period before is the same days of the month before, the debts are the customer balances screen's own
 * and the cash is the treasury statement's movements - each was a second answer to a question another
 * screen already answered. <b>Each card is drawn only for a reader holding the key of the screen it
 * summarises</b>, and what is not drawn was not read. A card opens that screen, in a tab.</p>
 */
@Log4j2
public class ModernDashboardApp {

    private static final DateTimeFormatter TREND_AXIS_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final String NOTHING = "—";

    /** Where the cards lead: the screens they summarise, opened as the sidebar opens them. */
    public interface Roads {
        void customerBalances() throws Exception;

        void itemSales(LocalDate from, LocalDate to) throws Exception;

        void treasuries() throws Exception;
    }

    private final SummaryService service;
    private final Roads roads;
    @Getter
    private final Region pane;
    private final ExecutorService loadExecutor;
    private final AtomicBoolean stopped = new AtomicBoolean();

    /** The ready period chosen, or null while the reader's own dates are. */
    private SummaryPeriod selectedPeriod = SummaryPeriod.TODAY;
    private LocalDate customFrom = LocalDate.now().minusDays(6);
    private LocalDate customTo = LocalDate.now();
    private HBox customRangeRow;
    private DatePicker fromPicker;
    private DatePicker toPicker;
    private Summary shown;

    private final Label comparedWith = new Label();
    private final Label lastUpdatedLabel = new Label();
    private Button refreshButton;
    private final Label nothingToShow = new Label(text("report.dashboard.nothing"));

    private StatCard salesCard;
    private final FigureLine salesChange = new FigureLine();
    private final Label salesNoChange = subtitle();
    private StatCard purchasesCard;
    private final FigureLine purchaseInvoices = new FigureLine();
    private StatCard cashCard;
    private final FigureLine cashIn = new FigureLine();
    private final FigureLine cashOut = new FigureLine();
    private StatCard invoicesCard;
    private final FigureLine salesDiscounts = new FigureLine();
    private StatCard receivablesCard;
    private final FigureLine debtors = new FigureLine();
    private HBox heroRow;

    private final TrendChart trendChart = new TrendChart("dashboard-trend-chart", 180);
    private VBox trendCard;
    private final PieChart.Data cashReceiptsSlice = new PieChart.Data(text("report.dashboard.cash.receipts"), 0);
    private final PieChart.Data cashOutSlice = new PieChart.Data(text("report.dashboard.cash.out"), 0);
    private final Label cashFlowEmptyLabel = new Label(text("report.dashboard.cash.flow.empty"));
    private final PieChart cashFlowChart = new PieChart();
    private VBox cashFlowCard;
    private HBox analyticsRow;

    private VBox lowStockCard;
    private VBox lowStockList;
    private Label lowStockHint;
    private VBox debtorsCard;
    private VBox debtorsList;
    private HBox firstListsRow;
    private VBox topItemsCard;
    private VBox topItemsList;
    private Label topItemsHint;
    private VBox treasuryCard;
    private VBox treasuryList;
    private HBox secondListsRow;

    public ModernDashboardApp(Roads roads) {
        this(new SummaryService(), roads);
    }

    public ModernDashboardApp(SummaryService service, Roads roads) {
        this.service = Objects.requireNonNull(service, "service");
        this.roads = Objects.requireNonNull(roads, "roads");
        this.loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modern-dashboard-refresh");
            thread.setDaemon(true);
            return thread;
        });

        VBox root = new VBox(18);
        root.getStyleClass().add("dashboard-root");
        root.setPadding(new Insets(20));
        nothingToShow.getStyleClass().add("dashboard-empty-label");
        nothingToShow.setVisible(false);
        nothingToShow.setManaged(false);
        root.getChildren().addAll(buildHeader(), customRangeRow, nothingToShow, buildHeroRow(), buildAnalyticsRow(),
                buildFirstListsRow(), buildSecondListsRow());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll-pane");
        scroll.getStylesheets().add(dashboardStylesheet());
        this.pane = scroll;

        cascadeAnimateIn(root.getChildren());

        // Replacing the main scene during logout or shutdown detaches this pane and must stop the
        // refresh executor; a closed tab does the same.
        pane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                stop();
            }
        });
        reload();
    }

    /**
     * The roads from the main screen: the customer balances screen - which opens on the debtors of today,
     * the card's own figure - the item sales on the summary's dates, and the treasuries' statement.
     */
    public static Roads roads(MainItems main, TabPane tabPane) {
        return new Roads() {
            @Override
            public void customerBalances() throws Exception {
                run(main.getAccountButtonsCustom(), tabPane);
            }

            @Override
            public void itemSales(LocalDate from, LocalDate to) throws Exception {
                run(main.getReportsButtons().itemSalesBetween(from, to), tabPane);
            }

            @Override
            public void treasuries() throws Exception {
                run(main.getTreasuryButtons().treasuryDetails(), tabPane);
            }
        };
    }

    private static void run(ButtonWithPerm button, TabPane tabPane) throws Exception {
        if (button.showOnTapPane()) {
            button.actionAddPaneToTabPane(tabPane);
        } else {
            button.action();
        }
    }

    private String dashboardStylesheet() {
        return Objects.requireNonNull(Main.class.getResource("css/dashboard.css")).toExternalForm();
    }

    // ------------------------------------------------------------------
    // Period selection
    // ------------------------------------------------------------------

    private ProfitLossPeriod currentRange() {
        return selectedPeriod == null ? new ProfitLossPeriod(customFrom, customTo) : selectedPeriod.range(LocalDate.now());
    }

    private String periodLabel(ProfitLossPeriod period) {
        if (selectedPeriod == SummaryPeriod.TODAY) {
            return text("report.dashboard.period.today");
        }
        if (selectedPeriod == SummaryPeriod.WEEK) {
            return text("report.dashboard.period.week.label");
        }
        if (selectedPeriod == SummaryPeriod.MONTH) {
            return text("report.dashboard.period.month.label");
        }
        return rangeText(period);
    }

    private Node buildPeriodSelector() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton today = periodToggle(text("report.dashboard.period.today"), SummaryPeriod.TODAY, group);
        ToggleButton week = periodToggle(text("report.dashboard.period.week.toggle"), SummaryPeriod.WEEK, group);
        ToggleButton month = periodToggle(text("report.dashboard.period.month.toggle"), SummaryPeriod.MONTH, group);
        ToggleButton custom = periodToggle(text("report.dashboard.period.custom"), null, group);
        today.setSelected(true);

        // A segmented group must always keep exactly one toggle selected - without this, clicking the
        // already-selected button deselects it with nothing to fall back on.
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        HBox box = new HBox(0, today, week, month, custom);
        box.getStyleClass().add("dashboard-period-selector");
        return box;
    }

    /** @param period the ready period, or null for the reader's own dates */
    private ToggleButton periodToggle(String label, SummaryPeriod period, ToggleGroup group) {
        ToggleButton button = new ToggleButton(label);
        button.getStyleClass().add("dashboard-period-toggle");
        button.setToggleGroup(group);
        button.setOnAction(e -> {
            if (!button.isSelected()) return;
            selectedPeriod = period;
            boolean isCustom = period == null;
            customRangeRow.setVisible(isCustom);
            customRangeRow.setManaged(isCustom);
            if (!isCustom) reload();
        });
        return button;
    }

    private void buildCustomRangeRow() {
        fromPicker = new DatePicker(customFrom);
        toPicker = new DatePicker(customTo);
        fromPicker.getStyleClass().add("dashboard-date-picker");
        toPicker.getStyleClass().add("dashboard-date-picker");

        Button apply = new Button(text("report.dashboard.apply"));
        apply.getStyleClass().add("btn-primary");
        apply.setOnAction(e -> {
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (from == null || to == null || from.isAfter(to)) {
                AllAlerts.handleError(text("report.dashboard.error.apply.custom.range.title"),
                        new UserValidationException(text("report.dashboard.error.invalid.date.range")));
                return;
            }
            customFrom = from;
            customTo = to;
            reload();
        });

        customRangeRow = new HBox(10, new Label(text("from")), fromPicker, new Label(text("to")), toPicker, apply);
        customRangeRow.getStyleClass().add("dashboard-custom-range-row");
        customRangeRow.setAlignment(Pos.CENTER_LEFT);
        customRangeRow.setVisible(false);
        customRangeRow.setManaged(false);
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private Node buildHeader() {
        Label title = new Label(text("report.dashboard.title"));
        title.getStyleClass().add("dashboard-header-title");
        Label subtitle = new Label(text("report.dashboard.subtitle"));
        subtitle.getStyleClass().add("dashboard-header-subtitle");
        comparedWith.getStyleClass().add("dashboard-header-subtitle");
        comparedWith.setId("dashboard-compared-with");
        // The books' currency (V80), said once rather than after every amount.
        String symbol = BaseCurrencySymbol.get();
        Label currency = new Label(LanguageManager.getInstance().getString("report.dashboard.currency", symbol));
        currency.getStyleClass().add("dashboard-header-subtitle");
        currency.setVisible(symbol != null && !symbol.isBlank());
        currency.setManaged(currency.isVisible());
        VBox titles = new VBox(2, title, subtitle, comparedWith, currency);

        buildCustomRangeRow();

        lastUpdatedLabel.getStyleClass().add("dashboard-last-updated");

        refreshButton = new Button(text("report.dashboard.refresh.button"));
        refreshButton.getStyleClass().add("btn-secondary");
        refreshButton.setOnAction(e -> reload());
        Button printButton = ListToolbar.printButton(this::print);
        printButton.setId("dashboard-print");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, titles, spacer, buildPeriodSelector(), lastUpdatedLabel, refreshButton, printButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void markLastUpdatedNow() {
        String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastUpdatedLabel.setText(LanguageManager.getInstance().getString("report.dashboard.last.updated", time));
    }

    // ------------------------------------------------------------------
    // Hero KPI row
    // ------------------------------------------------------------------

    private Node buildHeroRow() {
        salesCard = statCard(text("report.dashboard.card.sales"), "stat-accent-primary");
        salesCard.body.getChildren().addAll(salesChange.node(), salesNoChange);

        purchasesCard = statCard(text("report.dashboard.card.purchases"), "stat-accent-warning");
        purchasesCard.body.getChildren().add(purchaseInvoices.node());

        cashCard = statCard(text("report.dashboard.net.treasury"), "stat-accent-success");
        cashCard.body.getChildren().addAll(cashIn.node(), cashOut.node());
        makeClickable(cashCard.card, roads::treasuries);

        invoicesCard = statCard(text("report.dashboard.sales.invoices"), "stat-accent-neutral");
        invoicesCard.body.getChildren().add(salesDiscounts.node());

        receivablesCard = statCard(text("report.dashboard.customer.receivables"), "stat-accent-danger");
        Label asOfToday = subtitle();
        asOfToday.setText(text("report.dashboard.card.as.of.today"));
        receivablesCard.body.getChildren().addAll(debtors.node(), asOfToday);
        makeClickable(receivablesCard.card, roads::customerBalances);

        heroRow = new HBox(16, salesCard.card, purchasesCard.card, cashCard.card, invoicesCard.card,
                receivablesCard.card);
        for (Node card : heroRow.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        return heroRow;
    }

    private record StatCard(VBox card, VBox body, Label value) {
    }

    private StatCard statCard(String title, String accentClass) {
        Region accentBar = new Region();
        accentBar.getStyleClass().addAll("stat-accent-bar", accentClass);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");
        Label valueLabel = new Label("...");
        valueLabel.getStyleClass().add("stat-value");
        // Left to right, so a negative figure keeps its minus sign on the side a reader looks for it.
        valueLabel.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        VBox body = new VBox(4, titleLabel, valueLabel);
        body.setPadding(new Insets(14, 4, 0, 4));

        VBox card = new VBox(accentBar, body);
        card.getStyleClass().add("stat-card");
        return new StatCard(card, body, valueLabel);
    }

    // ------------------------------------------------------------------
    // Analytics row: sales trend + cash flow
    // ------------------------------------------------------------------

    private Node buildAnalyticsRow() {
        VBox.setVgrow(trendChart.chart(), Priority.ALWAYS);
        Label trendTitle = new Label(text("report.dashboard.trend.title"));
        trendTitle.getStyleClass().add("dashboard-card-title");
        trendCard = new VBox(10, trendTitle, trendChart.chart());
        trendCard.getStyleClass().addAll("dashboard-card", "dashboard-chart-card");
        HBox.setHgrow(trendCard, Priority.ALWAYS);

        cashFlowChart.getStyleClass().add("dashboard-cash-pie");
        cashFlowChart.setLegendVisible(true);
        cashFlowChart.setLabelsVisible(false);
        cashFlowChart.setAnimated(false);
        cashFlowChart.getData().addAll(cashReceiptsSlice, cashOutSlice);
        VBox.setVgrow(cashFlowChart, Priority.ALWAYS);

        cashFlowEmptyLabel.getStyleClass().add("dashboard-empty-label");
        cashFlowEmptyLabel.setVisible(false);
        cashFlowEmptyLabel.setManaged(false);

        Label cashTitle = new Label(text("report.dashboard.cash.flow.title"));
        cashTitle.getStyleClass().add("dashboard-card-title");
        cashFlowCard = new VBox(10, cashTitle, cashFlowChart, cashFlowEmptyLabel);
        cashFlowCard.getStyleClass().addAll("dashboard-card", "dashboard-chart-card");
        cashFlowCard.setPrefWidth(320);
        cashFlowCard.setMinWidth(280);

        analyticsRow = new HBox(16, trendCard, cashFlowCard);
        return analyticsRow;
    }

    // ------------------------------------------------------------------
    // Ranked list rows
    // ------------------------------------------------------------------

    private Node buildFirstListsRow() {
        lowStockCard = listCard(text("report.dashboard.low.stock.title"), text("report.dashboard.low.stock.hint"));
        lowStockList = (VBox) lowStockCard.getChildren().get(1);
        lowStockHint = (Label) ((VBox) lowStockCard.getChildren().get(0)).getChildren().get(1);

        debtorsCard = listCard(text("report.dashboard.top.debtors.title"),
                text("report.dashboard.hint.click.full.receivables"));
        debtorsList = (VBox) debtorsCard.getChildren().get(1);
        makeClickable(debtorsCard, roads::customerBalances);

        firstListsRow = new HBox(16, lowStockCard, debtorsCard);
        for (Node card : firstListsRow.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        return firstListsRow;
    }

    private Node buildSecondListsRow() {
        topItemsCard = listCard(text("report.dashboard.top.selling.title"), text("report.dashboard.hint.click.full.report"));
        topItemsList = (VBox) topItemsCard.getChildren().get(1);
        topItemsHint = (Label) ((VBox) topItemsCard.getChildren().get(0)).getChildren().get(1);
        makeClickable(topItemsCard, () -> {
            ProfitLossPeriod period = shown == null ? currentRange() : shown.period();
            roads.itemSales(period.from(), period.to());
        });

        treasuryCard = listCard(text("report.dashboard.treasury.balances.title"),
                text("report.dashboard.hint.click.treasury.details"));
        treasuryList = (VBox) treasuryCard.getChildren().get(1);
        makeClickable(treasuryCard, roads::treasuries);

        secondListsRow = new HBox(16, topItemsCard, treasuryCard);
        for (Node card : secondListsRow.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        return secondListsRow;
    }

    private VBox listCard(String title, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dashboard-card-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("dashboard-card-hint");
        VBox header = new VBox(2, titleLabel, hintLabel);

        VBox rows = new VBox(10);
        rows.setPadding(new Insets(10, 0, 0, 0));

        VBox card = new VBox(header, rows);
        card.getStyleClass().addAll("dashboard-card", "dashboard-list-card");
        return card;
    }

    /**
     * A name, its figure and - apart from the figure - the figure's unit. <b>A figure never shares a label
     * with an Arabic word</b>: the label takes its direction from the word, and "-7 / 5 قطعة" was drawn
     * "قطعة 5 / 7-", the minus on the far side of the seven. The figure is a left-to-right label of its own.
     */
    private Node listRow(String name, String figure, String unit, double ratio, String accentClass) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("dashboard-row-name");
        String valueClass = accentClass.equals("accent-danger") ? "dashboard-row-value-danger"
                : accentClass.equals("accent-warning") ? "dashboard-row-value-warning" : "dashboard-row-value";
        Label valueLabel = new Label(figure);
        valueLabel.getStyleClass().add(valueClass);
        valueLabel.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(4, nameLabel, spacer, valueLabel);
        if (unit != null && !unit.isBlank()) {
            Label unitLabel = new Label(unit);
            unitLabel.getStyleClass().add(valueClass);
            top.getChildren().add(unitLabel);
        }

        ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, ratio)));
        bar.getStyleClass().addAll("mini-bar", accentClass);
        bar.setMaxWidth(Double.MAX_VALUE);

        return new VBox(4, top, bar);
    }

    private Node emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dashboard-empty-label");
        return label;
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    private void reload() {
        if (stopped.get()) return;
        refreshButton.setDisable(true);
        ProfitLossPeriod range = currentRange();
        SummaryPeriod preset = selectedPeriod;
        LocalDate today = LocalDate.now();
        loadExecutor.submit(() -> {
            try {
                Summary summary = service.load(preset, range, today);
                if (stopped.get()) return;
                Platform.runLater(() -> {
                    if (stopped.get()) return;
                    apply(summary);
                    markLastUpdatedNow();
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                if (stopped.get()) return;
                log.error("Failed to refresh dashboard data: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    AllAlerts.reportError(text("report.dashboard.error.refresh.title"), e);
                });
            }
        });
    }

    private void apply(Summary summary) {
        shown = summary;
        comparedWith.setText(LanguageManager.getInstance().getString("report.dashboard.compared.with",
                rangeText(summary.previous())));
        comparedWith.setVisible(summary.shows(SummaryCard.SALES) || summary.shows(SummaryCard.CASH));

        show(salesCard.card, summary.shows(SummaryCard.SALES));
        show(invoicesCard.card, summary.shows(SummaryCard.SALES));
        show(trendCard, summary.shows(SummaryCard.SALES));
        if (summary.sales() != null) {
            money(salesCard.value, summary.sales().net());
            Optional<BigDecimal> change = summary.salesChange();
            if (change.isPresent()) {
                salesChange.show("report.dashboard.vs.previous.period", signedPercent(change.get()),
                        change.get().signum() < 0, null);
                salesNoChange.setText("");
            } else {
                salesChange.hide();
                salesNoChange.setText(text("report.dashboard.no.previous.period.data"));
            }
            invoicesCard.value.setText(String.valueOf(summary.sales().invoices()));
            salesDiscounts.show("report.dashboard.card.sales.discount", formatMoney(summary.sales().discount()), null);
            drawTrend(summary.trend());
        }

        show(purchasesCard.card, summary.shows(SummaryCard.PURCHASES));
        if (summary.purchases() != null) {
            money(purchasesCard.value, summary.purchases().net());
            purchaseInvoices.show("report.dashboard.card.purchase.invoices",
                    String.valueOf(summary.purchases().invoices()), null);
        }

        show(cashCard.card, summary.shows(SummaryCard.CASH));
        show(cashFlowCard, summary.shows(SummaryCard.CASH));
        if (summary.cash() != null) {
            money(cashCard.value, summary.cash().net());
            cashIn.show("report.dashboard.cash.receipts", formatMoney(summary.cash().in()), null);
            cashOut.show("report.dashboard.cash.out", formatMoney(summary.cash().out()), null);
            applyCashFlow(summary.cash());
        }

        show(receivablesCard.card, summary.shows(SummaryCard.RECEIVABLES));
        show(debtorsCard, summary.shows(SummaryCard.RECEIVABLES));
        if (summary.receivables() != null) {
            money(receivablesCard.value, summary.receivables().owed());
            debtors.show("report.dashboard.card.debtors", String.valueOf(summary.receivables().debtors()), null);
            applyDebtors(summary.receivables());
        }

        show(lowStockCard, summary.shows(SummaryCard.LOW_STOCK));
        if (summary.lowStock() != null) {
            applyLowStock(summary.lowStock());
        }

        show(topItemsCard, summary.shows(SummaryCard.TOP_ITEMS));
        topItemsHint.setText(LanguageManager.getInstance().getString("report.dashboard.period.hint.full.report",
                periodLabel(summary.period())));
        applyTopItems(summary.topItems());

        show(treasuryCard, summary.shows(SummaryCard.TREASURIES));
        applyTreasury(summary.treasuries());

        showRow(heroRow);
        showRow(analyticsRow);
        showRow(firstListsRow);
        showRow(secondListsRow);
        boolean nothing = summary.cards().isEmpty();
        nothingToShow.setVisible(nothing);
        nothingToShow.setManaged(nothing);
    }

    /** Every day of the fourteen, a quiet one as zero, time running left to right in either language. */
    private void drawTrend(List<TrendPoint> trend) {
        trendChart.clear();
        trendChart.addSeries(text("report.dashboard.card.sales"), trend,
                point -> point.day().format(TREND_AXIS_FORMAT), TrendPoint::net,
                TrendChart.classes("trend-sales", false), this::formatMoney);
    }

    private void applyCashFlow(CashFlow cash) {
        double receipts = cash.in().doubleValue();
        double out = cash.out().doubleValue();
        boolean empty = cash.isEmpty();

        cashReceiptsSlice.setName(text("report.dashboard.cash.receipts"));
        cashOutSlice.setName(text("report.dashboard.cash.out"));
        cashReceiptsSlice.setPieValue(empty ? 1 : receipts);
        cashOutSlice.setPieValue(empty ? 1 : out);

        cashFlowChart.setVisible(!empty);
        cashFlowChart.setManaged(!empty);
        cashFlowEmptyLabel.setVisible(empty);
        cashFlowEmptyLabel.setManaged(empty);
    }

    /**
     * The lowest items and how many there are in all. The quantities are the item's base unit, written as
     * quantities - a kilo and a half is not "2" - with the minimum beside the balance where one is set.
     */
    private void applyLowStock(LowStock lowStock) {
        lowStockList.getChildren().clear();
        lowStockHint.setText(lowStock.count() == 0 ? text("report.dashboard.low.stock.hint")
                : LanguageManager.getInstance().getString("report.dashboard.low.stock.count", lowStock.count()));
        if (lowStock.items().isEmpty()) {
            lowStockList.getChildren().add(emptyRow(text("report.dashboard.no.low.stock")));
            return;
        }
        for (LowStockItem item : lowStock.items()) {
            String accent = item.level() == StockLevel.NEGATIVE ? "accent-danger" : "accent-warning";
            String value = item.hasMinimum()
                    ? Columns.quantity(item.balance()) + " / " + Columns.quantity(item.minimum())
                    : Columns.quantity(item.balance());
            double ratio = item.hasMinimum() ? item.balance().doubleValue() / item.minimum().doubleValue() : 0;
            lowStockList.getChildren().add(listRow(item.name(), value, item.unitName(), ratio, accent));
        }
    }

    private void applyDebtors(Receivables receivables) {
        debtorsList.getChildren().clear();
        if (receivables.top().isEmpty()) {
            debtorsList.getChildren().add(emptyRow(text("report.dashboard.no.customer.receivables")));
            return;
        }
        double max = receivables.top().getFirst().balance().doubleValue();
        for (Receivables.Debtor debtor : receivables.top()) {
            double ratio = max > 0 ? debtor.balance().doubleValue() / max : 0;
            debtorsList.getChildren().add(listRow(debtor.name(), Columns.money(debtor.balance()), null, ratio,
                    "accent-primary"));
        }
    }

    private void applyTopItems(List<TopSellingItem> items) {
        topItemsList.getChildren().clear();
        if (items.isEmpty()) {
            topItemsList.getChildren().add(emptyRow(text("report.dashboard.no.sales.this.period")));
            return;
        }
        double max = items.stream().mapToDouble(i -> i.totalQuantity().doubleValue()).max().orElse(0);
        for (TopSellingItem item : items) {
            double qty = item.totalQuantity().doubleValue();
            double ratio = max > 0 ? qty / max : 0;
            // Base units, net of returns (TopSellingItemDao), so the quantity is written as one and named
            // in the item's own unit - "12 قطعة", not "12.00 وحدة".
            topItemsList.getChildren().add(listRow(item.itemName(), Columns.quantity(item.totalQuantity()),
                    item.unitName(), ratio, "accent-success"));
        }
    }

    private void applyTreasury(List<TreasuryBalanceSummary> balances) {
        treasuryList.getChildren().clear();
        if (balances.isEmpty()) {
            treasuryList.getChildren().add(emptyRow(text("report.dashboard.no.treasuries")));
            return;
        }
        double max = balances.stream().mapToDouble(b -> b.balance().abs().doubleValue()).max().orElse(0);
        for (TreasuryBalanceSummary balance : balances) {
            double value = balance.balance().doubleValue();
            double ratio = max > 0 ? Math.abs(value) / max : 0;
            String accent = balance.isNegative() ? "accent-danger" : "accent-success";
            treasuryList.getChildren().add(listRow(balance.name(), Columns.money(balance.balance()), null, ratio,
                    accent));
        }
    }

    // ------------------------------------------------------------------
    // Printing
    // ------------------------------------------------------------------

    /**
     * The figures this reader may see, this period beside the period before, with the trend above them when
     * the sales are among them. The lists are their own reports' papers and are not repeated here.
     */
    private void print() {
        if (shown == null || SummaryPaper.lines(shown).isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(pane.getScene().getWindow(), text("report.summary.accounts.title"));
        if (target != null) {
            writePaper(target);
        }
    }

    private void writePaper(File target) {
        byte[] picture = null;
        if (shown.shows(SummaryCard.SALES)) {
            try {
                picture = trendChart.png();
            } catch (IOException e) {
                AllAlerts.handleError(text("report.dashboard.error.refresh.title"), e);
                return;
            }
        }
        List<String[]> rows = new ArrayList<>();
        for (SummaryPaper.Line line : SummaryPaper.lines(shown)) {
            rows.add(new String[]{text(line.captionKey()), figure(line.current(), line.count()),
                    line.previous() == null ? NOTHING : figure(line.previous(), line.count())});
        }
        String[] headers = {text("report.dashboard.paper.figure"), rangeText(shown.period()), rangeText(shown.previous())};
        TablePdfLayout layout = new TablePdfLayout(headers, new float[]{3, 2, 2}, rows, null);
        String subtitle = periodLabel(shown.period()) + "\n"
                + LanguageManager.getInstance().getString("report.dashboard.compared.with", rangeText(shown.previous()));
        TablePdfReport.write(target, text("report.summary.accounts.title"), subtitle, picture, layout, () -> { });
    }

    private static String figure(BigDecimal value, boolean count) {
        return count ? Columns.quantity(value) : Columns.money(value);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** A row whose cards are all hidden takes no room either. */
    private static void showRow(HBox row) {
        boolean any = row.getChildren().stream().anyMatch(Node::isVisible);
        row.setVisible(any);
        row.setManaged(any);
    }

    private void money(Label label, BigDecimal value) {
        label.setText(formatMoney(value));
        label.pseudoClassStateChanged(Columns.NEGATIVE, value.signum() < 0);
    }

    /**
     * An amount as a plain figure. The currency is named once, in the header: written after every amount it
     * put an Arabic word into each figure's label, and a negative balance drew its minus on the far side.
     */
    private String formatMoney(BigDecimal value) {
        return Columns.money(value == null ? BigDecimal.ZERO : value);
    }

    /** "+6.30%" or "-4.20%": a sign, not an arrow, in a left-to-right label of its own. */
    private static String signedPercent(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + value.toPlainString() + "%";
    }

    /** "1 – 23 سبتمبر 2026" style: never a yyyy-MM-dd after an Arabic word. */
    private static String rangeText(ProfitLossPeriod period) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMMM yyyy", LanguageManager.getInstance().getCurrentLocale());
        if (period.from().equals(period.to())) {
            return day.format(period.from());
        }
        return LanguageManager.getInstance().getString("report.itemsales.period", day.format(period.from()),
                day.format(period.to()));
    }

    private static Label subtitle() {
        Label label = new Label();
        label.getStyleClass().add("stat-subtitle");
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            loadExecutor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Animation + interaction helpers
    // ------------------------------------------------------------------

    private void cascadeAnimateIn(List<Node> rows) {
        int delay = 80;
        for (Node row : rows) {
            animateTile(row, delay);
            delay += 120;
        }
    }

    private void animateTile(Node tile, int delayMillis) {
        tile.setOpacity(0);
        tile.setTranslateY(30);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(650), tile);
        fadeIn.setToValue(1.0);

        TranslateTransition slideUp = new TranslateTransition(Duration.millis(650), tile);
        slideUp.setToY(0);

        ParallelTransition animation = new ParallelTransition(fadeIn, slideUp);
        animation.setDelay(Duration.millis(delayMillis));
        animation.play();
    }

    private void makeClickable(Region card, TileAction action) {
        card.getStyleClass().add("clickable");
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                // handleError logs it, behind a reference code: logging it here as well wrote it twice.
                AllAlerts.handleError(text("report.dashboard.error.refresh.title"), ex);
            }
        });
    }

    @FunctionalInterface
    private interface TileAction {
        void run() throws Exception;
    }
}
