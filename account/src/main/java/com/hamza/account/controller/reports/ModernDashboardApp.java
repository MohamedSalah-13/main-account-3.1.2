package com.hamza.account.controller.reports;

import com.hamza.account.Main;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.features.notification.StockLevel;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import com.hamza.controlsfx.database.DaoException;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The home-tab overview screen: hero KPI cards, a sales trend, cash flow, and the
 * ranked lists (low stock / receivables / top items / treasury) that a business
 * owner actually needs to act on. Built by hand with plain JavaFX controls -
 * {@code StatCard}/{@code ListCard} are local helpers, not reusable framework -
 * so every visual is styled from {@code dashboard.css} against the app's existing
 * {@code -app-*} theme tokens and follows the light/dark/glass theme automatically.
 */
@Log4j2
public class ModernDashboardApp {

    private static final DateTimeFormatter TREND_AXIS_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private static Stage dashboardStage = null;
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    @Getter
    private final Region pane;
    private final ExecutorService loadExecutor;
    private final AtomicBoolean stopped = new AtomicBoolean();

    // Live-updated nodes, filled once in the constructor and refreshed in place.
    private Label salesTodayValue;
    private Label salesTodayDelta;
    private Label purchasesTodayValue;
    private Label netCashValue;
    private Label netCashSubtitle;
    private Label invoiceCountValue;
    private Label discountsSubtitle;
    private Label receivablesValue;
    private Label receivablesSubtitle;
    private Label lastUpdatedLabel;
    private Button refreshButton;

    private final XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
    private final PieChart.Data cashReceiptsSlice = new PieChart.Data("مقبوضات", 0);
    private final PieChart.Data cashOutSlice = new PieChart.Data("مدفوعات ومصروفات", 0);
    private Label cashFlowEmptyLabel;
    private PieChart cashFlowChart;

    private VBox lowStockList;
    private VBox topCustomersList;
    private VBox topItemsList;
    private VBox treasuryList;

    public ModernDashboardApp(DaoFactory daoFactory, DataPublisher dataPublisher) throws DaoException {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.loadExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modern-dashboard-refresh");
            thread.setDaemon(true);
            return thread;
        });

        DashboardMetrics metrics = loadMetrics();

        VBox root = new VBox(18);
        root.getStyleClass().add("dashboard-root");
        root.setPadding(new Insets(20));
        root.getChildren().addAll(buildHeader(), buildHeroRow(metrics), buildAnalyticsRow(), buildListsRow(true), buildListsRow(false));
        applyMetrics(metrics);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll-pane");
        scroll.getStylesheets().add(dashboardStylesheet());
        this.pane = scroll;

        cascadeAnimateIn(root.getChildren());

        // The dashboard is normally embedded through getPane(), so showWindow() and its
        // close handler are not involved. Replacing the main scene during logout or
        // shutdown detaches this pane and must stop the async refresh executor.
        pane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                stop();
            }
        });
    }

    private String dashboardStylesheet() {
        return java.util.Objects.requireNonNull(Main.class.getResource("css/dashboard.css")).toExternalForm();
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private Node buildHeader() {
        Label title = new Label("لوحة المؤشرات");
        title.getStyleClass().add("dashboard-header-title");
        Label subtitle = new Label("نظرة عامة على أداء العمل اليوم");
        subtitle.getStyleClass().add("dashboard-header-subtitle");
        VBox titles = new VBox(2, title, subtitle);

        lastUpdatedLabel = new Label();
        lastUpdatedLabel.getStyleClass().add("dashboard-last-updated");

        refreshButton = new Button("⟳ تحديث");
        refreshButton.getStyleClass().add("btn-secondary");
        refreshButton.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, titles, spacer, lastUpdatedLabel, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);
        markLastUpdatedNow();
        return header;
    }

    private void markLastUpdatedNow() {
        String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastUpdatedLabel.setText("آخر تحديث: " + time);
    }

    // ------------------------------------------------------------------
    // Hero KPI row
    // ------------------------------------------------------------------

    private Node buildHeroRow(DashboardMetrics m) {
        StatCard sales = statCard("مبيعات اليوم", "stat-accent-primary");
        salesTodayValue = sales.value;
        salesTodayDelta = new Label();
        salesTodayDelta.getStyleClass().add("stat-delta-up");
        sales.body.getChildren().add(salesTodayDelta);

        StatCard purchases = statCard("مشتريات اليوم", "stat-accent-warning");
        purchasesTodayValue = purchases.value;
        Label purchasesSubtitle = new Label(m.report.getPurchasesCountToday() + " فاتورة شراء");
        purchasesSubtitle.getStyleClass().add("stat-subtitle");
        purchases.body.getChildren().add(purchasesSubtitle);

        StatCard netCash = statCard("صافي الخزينة اليوم", "stat-accent-success");
        netCashValue = netCash.value;
        netCashSubtitle = new Label();
        netCashSubtitle.getStyleClass().add("stat-subtitle");
        netCash.body.getChildren().add(netCashSubtitle);

        StatCard invoices = statCard("فواتير المبيعات اليوم", "stat-accent-neutral");
        invoiceCountValue = invoices.value;
        discountsSubtitle = new Label();
        discountsSubtitle.getStyleClass().add("stat-subtitle");
        invoices.body.getChildren().add(discountsSubtitle);

        StatCard receivables = statCard("مستحقات العملاء", "stat-accent-danger");
        receivablesValue = receivables.value;
        receivablesSubtitle = new Label();
        receivablesSubtitle.getStyleClass().add("stat-subtitle");
        receivables.body.getChildren().add(receivablesSubtitle);
        makeClickable(receivables.card, this::openCustomerReceivables);

        HBox row = new HBox(16, sales.card, purchases.card, netCash.card, invoices.card, receivables.card);
        for (Node card : row.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        return row;
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
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);
        AreaChart<String, Number> trendChart = new AreaChart<>(xAxis, yAxis);
        trendChart.getStyleClass().add("dashboard-trend-chart");
        trendChart.setLegendVisible(false);
        trendChart.setCreateSymbols(true);
        trendChart.setAnimated(false);
        trendChart.getData().add(trendSeries);
        VBox.setVgrow(trendChart, Priority.ALWAYS);

        Label trendTitle = new Label("اتجاه المبيعات - آخر 14 يوم");
        trendTitle.getStyleClass().add("dashboard-card-title");
        VBox trendCard = new VBox(10, trendTitle, trendChart);
        trendCard.getStyleClass().addAll("dashboard-card", "dashboard-chart-card");
        HBox.setHgrow(trendCard, Priority.ALWAYS);

        cashFlowChart = new PieChart();
        cashFlowChart.getStyleClass().add("dashboard-cash-pie");
        cashFlowChart.setLegendVisible(true);
        cashFlowChart.setLabelsVisible(false);
        cashFlowChart.setAnimated(false);
        cashFlowChart.getData().addAll(cashReceiptsSlice, cashOutSlice);
        VBox.setVgrow(cashFlowChart, Priority.ALWAYS);

        cashFlowEmptyLabel = new Label("لا توجد حركة خزينة اليوم");
        cashFlowEmptyLabel.getStyleClass().add("dashboard-empty-label");
        cashFlowEmptyLabel.setVisible(false);
        cashFlowEmptyLabel.setManaged(false);

        Label cashTitle = new Label("حركة الخزينة اليوم");
        cashTitle.getStyleClass().add("dashboard-card-title");
        VBox cashCard = new VBox(10, cashTitle, cashFlowChart, cashFlowEmptyLabel);
        cashCard.getStyleClass().addAll("dashboard-card", "dashboard-chart-card");
        cashCard.setPrefWidth(320);
        cashCard.setMinWidth(280);

        HBox row = new HBox(16, trendCard, cashCard);
        return row;
    }

    // ------------------------------------------------------------------
    // Ranked list rows
    // ------------------------------------------------------------------

    private Node buildListsRow(boolean first) {
        if (first) {
            VBox lowStockCard = listCard("أصناف ناقصة الرصيد", "أرصدة الأصناف الأقل من الحد الأدنى");
            lowStockList = (VBox) lowStockCard.getChildren().get(1);

            VBox topCustomersCard = listCard("أعلى العملاء مديونية", "اضغط لعرض تقرير المستحقات الكامل");
            topCustomersList = (VBox) topCustomersCard.getChildren().get(1);
            makeClickable(topCustomersCard, this::openCustomerReceivables);

            HBox row = new HBox(16, lowStockCard, topCustomersCard);
            for (Node card : row.getChildren()) {
                HBox.setHgrow(card, Priority.ALWAYS);
            }
            return row;
        } else {
            VBox topItemsCard = listCard("الأكثر مبيعاً هذا الشهر", "اضغط لعرض التقرير الكامل");
            topItemsList = (VBox) topItemsCard.getChildren().get(1);
            makeClickable(topItemsCard, this::openItemSalesRank);

            VBox treasuryCard = listCard("أرصدة الخزائن", "اضغط لعرض تفاصيل الخزينة");
            treasuryList = (VBox) treasuryCard.getChildren().get(1);
            makeClickable(treasuryCard, this::openTreasuryDetails);

            HBox row = new HBox(16, topItemsCard, treasuryCard);
            for (Node card : row.getChildren()) {
                HBox.setHgrow(card, Priority.ALWAYS);
            }
            return row;
        }
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

    private Node listRow(String name, String valueText, double ratio, String accentClass) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("dashboard-row-name");
        Label valueLabel = new Label(valueText);
        valueLabel.getStyleClass().add(accentClass.equals("accent-danger") ? "dashboard-row-value-danger"
                : accentClass.equals("accent-warning") ? "dashboard-row-value-warning" : "dashboard-row-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(nameLabel, spacer, valueLabel);

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

    private record DashboardMetrics(
            DailyDashboardReport report,
            List<DailySalesPoint> trend,
            List<ItemsMiniQuantity> lowStockItems,
            List<CustomerReceivable> receivables,
            List<TreasuryBalance> treasuryBalances,
            List<TopSellingItem> topSellingItems
    ) {
    }

    private DashboardMetrics loadMetrics() throws DaoException {
        DailyDashboardReport report = daoFactory.dailyDashboardReportDao().loadAll().getFirst();
        List<DailySalesPoint> trend = daoFactory.dailySalesPointDao().getSalesTrend(14);

        List<ItemsMiniQuantity> lowStockItems = daoFactory.itemMiniDao().loadAll().stream()
                .sorted((a, b) -> Double.compare(a.getBalance(), b.getBalance()))
                .limit(5)
                .toList();

        List<CustomerReceivable> receivables = daoFactory.customerReceivableDao().getReceivablesReport();

        List<TreasuryBalance> treasuryBalances = daoFactory.treasuryBalanceDao().getSumTreasuryBalance();

        List<TopSellingItem> topSellingItems = daoFactory.topSellingItemDao().loadAll().stream()
                .limit(5)
                .toList();

        return new DashboardMetrics(report, trend, lowStockItems, receivables, treasuryBalances, topSellingItems);
    }

    private void refresh() {
        if (stopped.get()) return;
        refreshButton.setDisable(true);
        loadExecutor.submit(() -> {
            try {
                DashboardMetrics metrics = loadMetrics();
                if (stopped.get()) return;
                Platform.runLater(() -> {
                    if (stopped.get()) return;
                    applyMetrics(metrics);
                    markLastUpdatedNow();
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                if (stopped.get()) return;
                log.error("Failed to refresh dashboard data: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    com.hamza.controlsfx.alert.AllAlerts.reportError("تحديث لوحة المؤشرات", e);
                });
            }
        });
    }

    private void applyMetrics(DashboardMetrics m) {
        DailyDashboardReport report = m.report();

        salesTodayValue.setText(formatMoney(report.getSalesTotalToday()));
        applyDelta(salesTodayDelta, report.getSalesTotalToday(), report.getSalesTotalYesterday());

        purchasesTodayValue.setText(formatMoney(report.getPurchasesTotalToday()));

        BigDecimal netCash = report.getTotalReceiptsToday().subtract(report.getTotalPaymentsAndExpensesToday());
        netCashValue.setText(formatMoney(netCash));
        netCashSubtitle.setText("مقبوضات " + formatMoney(report.getTotalReceiptsToday())
                + " / مدفوعات " + formatMoney(report.getTotalPaymentsAndExpensesToday()));

        invoiceCountValue.setText(String.valueOf(report.getSalesCountToday()));
        discountsSubtitle.setText("خصومات اليوم: " + formatMoney(report.getTotalDiscountsToday()));

        double totalReceivable = m.receivables().stream().mapToDouble(CustomerReceivable::getTotalReceivable).sum();
        receivablesValue.setText(formatMoney(totalReceivable));
        receivablesSubtitle.setText(m.receivables().size() + " عميل مدين - اضغط للتفاصيل");

        applyTrend(m.trend());
        applyCashFlow(report);
        applyLowStock(m.lowStockItems());
        applyTopCustomers(m.receivables());
        applyTopItems(m.topSellingItems());
        applyTreasury(m.treasuryBalances());
    }

    private void applyDelta(Label deltaLabel, BigDecimal today, BigDecimal yesterday) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            deltaLabel.setText("لا توجد بيانات أمس للمقارنة");
            deltaLabel.getStyleClass().setAll("stat-subtitle");
            return;
        }
        double pct = today.subtract(yesterday)
                .divide(yesterday, 4, RoundingMode.HALF_UP)
                .doubleValue() * 100;
        boolean up = pct >= 0;
        deltaLabel.setText((up ? "▲ " : "▼ ") + String.format(Locale.US, "%.1f%%", Math.abs(pct)) + " عن الأمس");
        deltaLabel.getStyleClass().setAll(up ? "stat-delta-up" : "stat-delta-down");
    }

    private void applyTrend(List<DailySalesPoint> trend) {
        trendSeries.getData().clear();
        for (DailySalesPoint point : trend) {
            trendSeries.getData().add(new XYChart.Data<>(point.getDate().format(TREND_AXIS_FORMAT), point.getTotal().doubleValue()));
        }
    }

    private void applyCashFlow(DailyDashboardReport report) {
        double receipts = report.getTotalReceiptsToday().doubleValue();
        double out = report.getTotalPaymentsAndExpensesToday().doubleValue();
        boolean empty = receipts == 0 && out == 0;

        cashReceiptsSlice.setName("مقبوضات " + formatMoney(receipts));
        cashOutSlice.setName("مدفوعات ومصروفات " + formatMoney(out));
        cashReceiptsSlice.setPieValue(empty ? 1 : receipts);
        cashOutSlice.setPieValue(empty ? 1 : out);

        cashFlowChart.setVisible(!empty);
        cashFlowChart.setManaged(!empty);
        cashFlowEmptyLabel.setVisible(empty);
        cashFlowEmptyLabel.setManaged(empty);
    }

    private void applyLowStock(List<ItemsMiniQuantity> items) {
        lowStockList.getChildren().clear();
        if (items.isEmpty()) {
            lowStockList.getChildren().add(emptyRow("لا توجد أصناف ناقصة الرصيد حالياً"));
            return;
        }
        for (ItemsMiniQuantity item : items) {
            StockLevel level = StockLevel.of(item.getBalance(), item.getMiniQuantity());
            String accent = level == StockLevel.NEGATIVE ? "accent-danger" : "accent-warning";
            String value = String.format(Locale.US, "%.0f / %.0f", item.getBalance(), item.getMiniQuantity());
            double ratio = item.getMiniQuantity() > 0 ? item.getBalance() / item.getMiniQuantity() : 0;
            lowStockList.getChildren().add(listRow(item.getNameItem(), value, ratio, accent));
        }
    }

    private void applyTopCustomers(List<CustomerReceivable> receivables) {
        topCustomersList.getChildren().clear();
        List<CustomerReceivable> top = receivables.stream()
                .sorted((a, b) -> Double.compare(b.getTotalReceivable(), a.getTotalReceivable()))
                .limit(5)
                .toList();
        if (top.isEmpty()) {
            topCustomersList.getChildren().add(emptyRow("لا توجد مستحقات على العملاء"));
            return;
        }
        double max = top.getFirst().getTotalReceivable();
        for (CustomerReceivable customer : top) {
            double ratio = max > 0 ? customer.getTotalReceivable() / max : 0;
            topCustomersList.getChildren().add(listRow(customer.getCustomerName(), formatMoney(customer.getTotalReceivable()), ratio, "accent-primary"));
        }
    }

    private void applyTopItems(List<TopSellingItem> items) {
        topItemsList.getChildren().clear();
        if (items.isEmpty()) {
            topItemsList.getChildren().add(emptyRow("لا توجد مبيعات مسجلة هذا الشهر"));
            return;
        }
        double max = items.stream().mapToDouble(i -> i.getTotalQuantity().doubleValue()).max().orElse(0);
        for (TopSellingItem item : items) {
            double qty = item.getTotalQuantity().doubleValue();
            double ratio = max > 0 ? qty / max : 0;
            topItemsList.getChildren().add(listRow(item.getItemName(), formatMoney(qty) + " وحدة", ratio, "accent-success"));
        }
    }

    private void applyTreasury(List<TreasuryBalance> balances) {
        treasuryList.getChildren().clear();
        if (balances.isEmpty()) {
            treasuryList.getChildren().add(emptyRow("لا توجد خزائن مسجلة"));
            return;
        }
        double max = balances.stream().mapToDouble(TreasuryBalance::getBalance).map(Math::abs).max().orElse(0);
        for (TreasuryBalance balance : balances) {
            double ratio = max > 0 ? Math.abs(balance.getBalance()) / max : 0;
            String accent = balance.getBalance() < 0 ? "accent-danger" : "accent-success";
            treasuryList.getChildren().add(listRow(balance.getName(), formatMoney(balance.getBalance()), ratio, accent));
        }
    }

    private String formatMoney(BigDecimal value) {
        return formatMoney(value == null ? 0 : value.doubleValue());
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "%,.2f ج.م", value);
    }

    // ------------------------------------------------------------------
    // Window entry point / lifecycle (unchanged behaviour)
    // ------------------------------------------------------------------

    public void showWindow() {
        if (dashboardStage != null && dashboardStage.isShowing()) {
            dashboardStage.toFront();
            dashboardStage.requestFocus();
            return;
        }

        if (dashboardStage == null) {
            dashboardStage = new Stage();

            javafx.scene.Scene scene = new javafx.scene.Scene(pane, 1100, 850);
            ThemeManager.apply(scene);

            dashboardStage.setTitle("لوحة المتابعة اليومية - Dashboard");
            dashboardStage.setScene(scene);
            dashboardStage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));

            dashboardStage.setOnCloseRequest(event -> {
                stop();
                dashboardStage = null;
            });
        }

        dashboardStage.show();
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
                log.error("Failed to open dashboard card detail screen", ex);
            }
        });
    }

    private void openCustomerReceivables() throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/CustomerReceivableView.fxml"));
        Parent root = loader.load();

        CustomerReceivableController controller = loader.getController();
        controller.setDaoFactory(daoFactory);

        StageManager.show("customer-receivables", new SceneAll(root), "مستحقات العملاء");
    }

    private void openTreasuryDetails() throws Exception {
        new OpenTreasuryDetailsApplication(daoFactory, dataPublisher).start(new Stage());
    }

    private void openItemSalesRank() throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/reports/ItemSalesRankView.fxml"));
        Parent root = loader.load();

        ItemSalesRankController controller = loader.getController();
        controller.setDaoFactory(daoFactory);

        StageManager.show("item-sales-rank", new SceneAll(root), "تقرير حركة الأصناف (الأكثر والأقل مبيعاً)");
    }

    @FunctionalInterface
    private interface TileAction {
        void run() throws Exception;
    }
}
