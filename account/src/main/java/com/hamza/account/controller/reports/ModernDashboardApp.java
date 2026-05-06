package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DailyDashboardReport;
import com.hamza.controlsfx.database.DaoException;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import eu.hansolo.tilesfx.chart.ChartData;
import eu.hansolo.tilesfx.skins.BarChartItem;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ModernDashboardApp extends Application {

    private static final Color TILE_BACKGROUND = Color.web("#2a2a2a");
    private static final Color MAIN_BACKGROUND = Color.web("#1d1d1d");
//    private static final Color MAIN_BACKGROUND = Color.WHEAT;

    @Getter
    private final GridPane pane;
    private final ScheduledExecutorService scheduler;

    public ModernDashboardApp(DaoFactory daoFactory) throws DaoException {
        // 1. جلب البيانات من قاعدة البيانات
//        DaoFactory daoFactory = getDummyReport();
        DailyDashboardReport report = daoFactory.dailyDashboardReportDao().loadAll().getFirst();

        // 2. إنشاء البطاقات (Tiles)

        Tile salesTodayTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("مبيعات اليوم")
                .text("إجمالي قيمة الفواتير")
                .value(report.getSalesTotalToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(TILE_BACKGROUND)
                .barColor(Tile.BLUE)
                .build();

        Tile purchasesTodayTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("مشتريات اليوم")
                .text("إجمالي قيمة الفواتير")
                .value(report.getPurchasesTotalToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(TILE_BACKGROUND)
                .barColor(Tile.ORANGE)
                .build();

        Tile salesCountTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("عدد الفواتير")
                .titleAlignment(TextAlignment.CENTER)
                .description("فاتورة مبيعات اليوم")
                .value(report.getSalesCountToday().doubleValue())
                .backgroundColor(TILE_BACKGROUND)
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
                // 👇 هنا التغيير الأهم: نستخدم barChartItems بدلاً من chartData
                .barChartItems(todayData, yesterdayData, weekData, monthData)
                .backgroundColor(TILE_BACKGROUND)
                // .barColor(Tile.ORANGE) <-- (اختياري) يمكنك إزالتها لأننا حددنا لوناً لكل عنصر بالأعلى
                .build();


        var data = new ChartData("مقبوضات", report.getTotalReceiptsToday().doubleValue(), Tile.GREEN);
        var data1 = new ChartData("مدفوعات ومصروفات", report.getTotalPaymentsAndExpensesToday().doubleValue(), Tile.RED);

        Tile cashFlowTile = TileBuilder.create()
                .skinType(Tile.SkinType.DONUT_CHART)
                .title("حركة الخزينة")
                .text("المقبوضات vs المصروفات")
                .chartData(
                        data,
                        data1
                )
                .backgroundColor(TILE_BACKGROUND)
                .build();

        Tile discountsTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("خصومات اليوم")
                .text("الممنوحة والمكتسبة")
                .value(report.getTotalDiscountsToday().doubleValue())
                .unit("ج.م")
                .decimals(2)
                .backgroundColor(TILE_BACKGROUND)
                .barColor(Tile.MAGENTA)
                .build();

        // 3. إعداد الـ Layout (الشبكة)
        pane = new GridPane();
        pane.setHgap(20);
        pane.setVgap(20);
        pane.setPadding(new Insets(20));
        pane.setBackground(new Background(new BackgroundFill(MAIN_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));

        salesComparisonTile.setPrefSize(520, 250);

        pane.add(salesTodayTile, 0, 0);
        pane.add(purchasesTodayTile, 1, 0);
        pane.add(salesCountTile, 2, 0);

        pane.add(salesComparisonTile, 0, 1, 2, 1);
        pane.add(cashFlowTile, 2, 1);

        pane.add(discountsTile, 0, 2);

        // ==========================================
        // 4. تطبيق حركة الدخول المتسلسل (Cascade Animation)
        // ==========================================
        // تأخير متزايد لكل بطاقة لتعطي تأثير الدخول المتتالي
        int delay = 100;
        animateTile(salesTodayTile, delay);
        animateTile(purchasesTodayTile, delay += 150);
        animateTile(salesCountTile, delay += 150);
        animateTile(salesComparisonTile, delay += 150);
        animateTile(cashFlowTile, delay += 150);
        animateTile(discountsTile, delay += 150);

        // إنشاء مؤقت يعمل في الخلفية
        scheduler = Executors.newScheduledThreadPool(1);

// تشغيل دالة كل 5 ثواني
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. جلب التقرير الجديد من قاعدة البيانات
//                DailyDashboardReport freshReport = getDummyReport();
                var first = daoFactory.dailyDashboardReportDao().loadAll().getFirst();

                // 2. تحديث الواجهة (يجب أن يتم دائماً داخل Platform.runLater في JavaFX)
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
                });

            } catch (Exception e) {
                log.error("Error fetching dashboard data: {}", e.getMessage(), e);
            }
        }, 3, 5, TimeUnit.SECONDS); // يبدأ بعد 3 ثواني، ثم يتكرر كل 5 ثواني

    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // 5. إعداد وعرض الشاشة
        Scene scene = new Scene(pane, 850, 850);
        primaryStage.setTitle("لوحة المتابعة اليومية - Dashboard");
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().reports));
        primaryStage.show();
// تأكد من إيقاف المؤقت عند إغلاق البرنامج لتجنب بقائه في الذاكرة
        primaryStage.setOnCloseRequest(event -> scheduler.shutdownNow());
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

}