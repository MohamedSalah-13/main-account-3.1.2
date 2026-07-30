package com.hamza.account.controller.reports;

import com.hamza.account.Main;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.features.notification.NotifyItemAlert;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.account.model.domain.DailyDashboardReport;
import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.view.OpenTreasuryDetailsApplication;
import com.hamza.account.view.SceneAll;
import com.hamza.account.view.StageManager;
import com.hamza.controlsfx.database.DaoException;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import eu.hansolo.tilesfx.chart.ChartData;
import eu.hansolo.tilesfx.skins.BarChartItem;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ModernDashboardApp {

    // مرجع ثابت للنافذة لمنع تكرارها
    private static Stage dashboardStage = null;
    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    private final Color tileBackground;
    private final Color tileTitleColor;
    private final Color tileTextColor;
    private final Color tileValueColor;
    @Getter
    private final GridPane pane;
    private final ScheduledExecutorService scheduler;

    public ModernDashboardApp(DaoFactory daoFactory, DataPublisher dataPublisher) throws DaoException {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;

        // ألوان متوافقة مع ثيم التطبيق الحالي (فاتح/غامق/زجاجي) بدل ألوان ثابتة
        var theme = ThemeManager.getCurrentTheme();
        tileBackground = switch (theme) {
            case DARK -> Color.web("#1e293b");
            case GLASS -> Color.web("#ffffff", 0.12);
            case LIGHT -> Color.web("#ffffff");
        };
        tileTitleColor = theme == ThemeManager.Theme.LIGHT ? Color.web("#172554") : Color.web("#e2e8f0");
        tileTextColor = theme == ThemeManager.Theme.LIGHT ? Color.web("#64748b") : Color.web("#94a3b8");
        tileValueColor = theme == ThemeManager.Theme.LIGHT ? Color.web("#172554") : Color.web("#f1f5f9");

        // 1. جلب البيانات من قاعدة البيانات
        DailyDashboardReport report = daoFactory.dailyDashboardReportDao().loadAll().getFirst();
        // 2. إنشاء البطاقات (Tiles)

        Tile salesTodayTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("مبيعات اليوم")
                .text("إجمالي قيمة الفواتير")
                .value(report.getSalesTotalToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.BLUE)
                .build();

        Tile purchasesTodayTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("مشتريات اليوم")
                .text("إجمالي قيمة الفواتير")
                .value(report.getPurchasesTotalToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.ORANGE)
                .build();

        Tile salesCountTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("عدد الفواتير")
                .titleAlignment(TextAlignment.CENTER)
                .description("فاتورة مبيعات اليوم")
                .value(report.getSalesCountToday().doubleValue())
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.ORANGE)
                .build();

        // 1. استخدام BarChartItem بدلاً من ChartData
        BarChartItem todayData = new BarChartItem("اليوم", report.getSalesTotalToday().doubleValue(), Tile.BLUE);
        BarChartItem yesterdayData = new BarChartItem("الأمس", report.getSalesTotalYesterday().doubleValue(), Tile.LIGHT_RED);
        BarChartItem weekData = new BarChartItem("الأسبوع", report.getSalesTotalWeek().doubleValue(), Tile.GREEN);
        BarChartItem monthData = new BarChartItem("الشهر", report.getSalesTotalMonth().doubleValue(), Tile.YELLOW);

        // 2. إنشاء البطاقة وتمرير المتغيرات إليها
        Tile salesComparisonTile = TileBuilder.create()
                .skinType(Tile.SkinType.BAR_CHART)
                .title("مقارنة المبيعات")
                .text("تحديث حي للفترات الزمنية")
                .animated(true)
                .animationDuration(800)
                .barChartItems(todayData, yesterdayData, weekData, monthData)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .build();

        var data = new ChartData("مقبوضات", report.getTotalReceiptsToday().doubleValue(), Tile.GREEN);
        var data1 = new ChartData("مدفوعات ومصروفات", report.getTotalPaymentsAndExpensesToday().doubleValue(), Tile.RED);

        Tile cashFlowTile = TileBuilder.create()
                .skinType(Tile.SkinType.DONUT_CHART)
                .title("حركة الخزينة")
                .text("المقبوضات vs المصروفات")
                .chartData(data, data1)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .build();

        Tile discountsTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("خصومات اليوم")
                .text("الممنوحة والمكتسبة")
                .value(report.getTotalDiscountsToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.MAGENTA)
                .build();

        // بطاقات تفاعلية جديدة: كل واحدة قابلة للنقر لفتح التقرير التفصيلي بتاعها
        List<ItemsMiniQuantity> lowStockItems = daoFactory.itemMiniDao().loadAll();
        Tile lowStockTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("أصناف ناقصة الرصيد")
                .text("اضغط لعرض القائمة")
                .value(lowStockItems.size())
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(lowStockItems.isEmpty() ? tileValueColor : Tile.RED)
                .barColor(Tile.RED)
                .build();
        makeClickable(lowStockTile, this::openLowStockItems);

        List<CustomerReceivable> receivables = daoFactory.customerReceivableDao().getReceivablesReport();
        double totalReceivable = receivables.stream().mapToDouble(CustomerReceivable::getTotalReceivable).sum();
        Tile receivablesTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("مستحقات العملاء")
                .text(receivables.size() + " عميل مدين - اضغط للتفاصيل")
                .value(totalReceivable)
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.MAGENTA)
                .build();
        makeClickable(receivablesTile, this::openCustomerReceivables);

        List<TreasuryBalance> treasuryBalances = daoFactory.treasuryBalanceDao().getSumTreasuryBalance();
        double totalCash = treasuryBalances.stream().mapToDouble(TreasuryBalance::getBalance).sum();
        Tile treasuryTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("رصيد الخزينة")
                .text("إجمالي كل الخزائن - اضغط للتفاصيل")
                .value(totalCash)
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .valueColor(tileValueColor)
                .barColor(Tile.GREEN)
                .build();
        makeClickable(treasuryTile, this::openTreasuryDetails);

        Tile topSellersTile = maxItems(daoFactory);
        makeClickable(topSellersTile, this::openItemSalesRank);

        // 3. إعداد الـ Layout (الشبكة) - بدون خلفية ثابتة عشان يورث خلفية الثيم الحالي
        pane = new GridPane();
        pane.setHgap(20);
        pane.setVgap(20);
        pane.setPadding(new Insets(20));

        salesComparisonTile.setPrefSize(520, 250);
        topSellersTile.setPrefSize(520, 250);

        pane.add(salesTodayTile, 0, 0);
        pane.add(purchasesTodayTile, 1, 0);
        pane.add(salesCountTile, 2, 0);

        pane.add(salesComparisonTile, 0, 1, 2, 1);
        pane.add(cashFlowTile, 2, 1);

        pane.add(discountsTile, 0, 2);
        pane.add(lowStockTile, 1, 2);
        pane.add(receivablesTile, 2, 2);

        pane.add(topSellersTile, 0, 3, 2, 1);
        pane.add(treasuryTile, 2, 3);

        // ==========================================
        // 4. تطبيق حركة الدخول المتسلسل (Cascade Animation)
        // ==========================================
        int delay = 100;
        animateTile(salesTodayTile, delay);
        animateTile(purchasesTodayTile, delay += 150);
        animateTile(salesCountTile, delay += 150);
        animateTile(salesComparisonTile, delay += 150);
        animateTile(cashFlowTile, delay += 150);
        animateTile(discountsTile, delay += 150);
        animateTile(lowStockTile, delay += 150);
        animateTile(receivablesTile, delay += 150);
        animateTile(topSellersTile, delay += 150);
        animateTile(treasuryTile, delay += 150);

        // إنشاء مؤقت يعمل في الخلفية - تحديث حي كل 5 ثواني
        scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                var first = daoFactory.dailyDashboardReportDao().loadAll().getFirst();
                var freshLowStockCount = daoFactory.itemMiniDao().loadAll().size();
                var freshReceivables = daoFactory.customerReceivableDao().getReceivablesReport();
                var freshReceivableTotal = freshReceivables.stream().mapToDouble(CustomerReceivable::getTotalReceivable).sum();
                var freshCash = daoFactory.treasuryBalanceDao().getSumTreasuryBalance().stream()
                        .mapToDouble(TreasuryBalance::getBalance).sum();

                Platform.runLater(() -> {
                    todayData.setValue(first.getSalesTotalToday().doubleValue());
                    yesterdayData.setValue(first.getSalesTotalYesterday().doubleValue());
                    weekData.setValue(first.getSalesTotalWeek().doubleValue());
                    monthData.setValue(first.getSalesTotalMonth().doubleValue());

                    salesTodayTile.setValue(first.getSalesTotalToday().doubleValue());
                    purchasesTodayTile.setValue(first.getPurchasesTotalToday().doubleValue());
                    salesCountTile.setValue(first.getSalesCountToday());

                    data.setValue(first.getTotalReceiptsToday().doubleValue());
                    data1.setValue(first.getTotalPaymentsAndExpensesToday().doubleValue());

                    discountsTile.setValue(first.getTotalDiscountsToday().doubleValue());

                    lowStockTile.setValue(freshLowStockCount);
                    receivablesTile.setValue(freshReceivableTotal);
                    treasuryTile.setValue(freshCash);
                });

            } catch (Exception e) {
                log.error("Error fetching dashboard data: {}", e.getMessage(), e);
            }
        }, 3, 5, TimeUnit.SECONDS); // يبدأ بعد 3 ثواني، ثم يتكرر كل 5 ثواني

    }

    public void showWindow() {
        // 1. التحقق مما إذا كانت النافذة مفتوحة بالفعل
        if (dashboardStage != null && dashboardStage.isShowing()) {
            dashboardStage.toFront();    // إحضارها للمقدمة
            dashboardStage.requestFocus(); // وضع التركيز عليها
            return; // إنهاء الدالة هنا لمنع إنشاء نافذة جديدة
        }

        // 2. إذا كانت النافذة غير موجودة أو أُغلقت سابقاً، ننشئها من جديد
        if (dashboardStage == null) {
            dashboardStage = new Stage();

            Scene scene = new Scene(pane, 850, 850);
            ThemeManager.apply(scene);

            dashboardStage.setTitle("لوحة المتابعة اليومية - Dashboard");
            dashboardStage.setScene(scene);
            dashboardStage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));

            // 3. التعامل مع حدث الإغلاق
            dashboardStage.setOnCloseRequest(event -> {
                if (scheduler != null) {
                    scheduler.shutdownNow(); // إيقاف المؤقت
                }
                dashboardStage = null; // تفريغ المرجع لكي نتمكن من فتحها مرة أخرى
            });
        }

        dashboardStage.show();
    }

    /**
     * دالة لتطبيق حركتي الظهور والانزلاق على أي عنصر واجهة (Node)
     */
    private void animateTile(Node tile, int delayMillis) {
        // تهيئة العنصر ليكون مخفياً وأسفل مكانه الطبيعي
        tile.setOpacity(0);
        tile.setTranslateY(40);

        // 1. حركة الظهور (Fade In)
        FadeTransition fadeIn = new FadeTransition(Duration.millis(800), tile);
        fadeIn.setToValue(1.0);

        // 2. حركة الانزلاق للأعلى (Slide Up)
        TranslateTransition slideUp = new TranslateTransition(Duration.millis(800), tile);
        slideUp.setToY(0);

        // دمج الحركتين معاً
        ParallelTransition animation = new ParallelTransition(fadeIn, slideUp);
        animation.setDelay(Duration.millis(delayMillis)); // وقت التأخير قبل بدء الحركة
        animation.play();
    }

    /**
     * يجعل البطاقة قابلة للنقر: مؤشر يد، وتكبير بسيط عند المرور عليها، مع تنفيذ
     * الإجراء عند الضغط.
     */
    private void makeClickable(Tile tile, TileAction action) {
        tile.setCursor(Cursor.HAND);
        tile.setOnMouseEntered(e -> {
            tile.setScaleX(1.02);
            tile.setScaleY(1.02);
        });
        tile.setOnMouseExited(e -> {
            tile.setScaleX(1.0);
            tile.setScaleY(1.0);
        });
        tile.setOnMouseClicked(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                log.error("Failed to open dashboard tile detail screen", ex);
            }
        });
    }

    private void openLowStockItems() throws Exception {
        new NotifyItemAlert(daoFactory.itemMiniDao().loadAll()).action();
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

    private Tile maxItems(DaoFactory daoFactory) throws DaoException {
        var topSellingItems = daoFactory.topSellingItemDao().loadAll()
                .stream().limit(5).toList();
        List<BarChartItem> barChartItems = new ArrayList<>();
        List<Color> colors = List.of(Tile.BLUE, Tile.ORANGE, Tile.GREEN, Tile.YELLOW, Tile.MAGENTA);

        for (int i = 0; i < topSellingItems.size(); i++) {
            TopSellingItem item = topSellingItems.get(i);
            BarChartItem itemData = new BarChartItem(item.getItemName(), item.getTotalQuantity().doubleValue(), colors.get(i % colors.size()));
            barChartItems.add(itemData);
        }

        return TileBuilder.create()
                .skinType(Tile.SkinType.BAR_CHART)
                .title("أكثر الأصناف مبيعاً هذا الشهر")
                .text("اضغط لعرض التقرير الكامل")
                .animated(true)
                .animationDuration(800)
                .barChartItems(barChartItems)
                .backgroundColor(tileBackground)
                .titleColor(tileTitleColor)
                .textColor(tileTextColor)
                .build();
    }

    @FunctionalInterface
    private interface TileAction {
        void run() throws Exception;
    }

}
