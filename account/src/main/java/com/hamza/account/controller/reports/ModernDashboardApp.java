package com.hamza.account.controller.reports;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DailyDashboardReport;
import com.hamza.controlsfx.database.DaoException;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import eu.hansolo.tilesfx.chart.ChartData;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class ModernDashboardApp extends Application {

    private static final Color TILE_BACKGROUND = Color.web("#2a2a2a");
    private static final Color MAIN_BACKGROUND = Color.web("#1d1d1d");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws DaoException {
        // 1. جلب البيانات (هنا نستخدم بيانات وهمية للتجربة)
        DailyDashboardReport report = getDummyReport();

        // 2. إنشاء البطاقات (Tiles)

        // بطاقة إجمالي المبيعات اليوم
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

        // بطاقة إجمالي المشتريات اليوم
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

        // بطاقة عدد فواتير المبيعات
        Tile salesCountTile = TileBuilder.create()
                .skinType(Tile.SkinType.NUMBER)
                .title("عدد الفواتير")
                .titleAlignment(TextAlignment.CENTER)
                .description("فاتورة مبيعات اليوم")
                .value(report.getSalesCountToday().doubleValue())
//                .unit("ج.م")
//                .decimals(2)
                .backgroundColor(TILE_BACKGROUND)
                .barColor(Tile.ORANGE)
                .build();

        // رسم بياني شريطي لمقارنة المبيعات (اليوم، الأمس، الأسبوع، الشهر)
        Tile salesComparisonTile = TileBuilder.create()
                .skinType(Tile.SkinType.BAR_CHART)
                .title("مقارنة المبيعات")
                .text("مقارنة الفترات الزمنية")
                .chartData(
                        new ChartData("اليوم", report.getSalesTotalToday().doubleValue(), Tile.BLUE),
                        new ChartData("الأمس", report.getSalesTotalYesterday().doubleValue(), Tile.LIGHT_RED),
                        new ChartData("الأسبوع", report.getSalesTotalWeek().doubleValue(), Tile.GREEN),
                        new ChartData("الشهر", report.getSalesTotalMonth().doubleValue(), Tile.YELLOW)
                )
                .backgroundColor(TILE_BACKGROUND)
                .barColor(Tile.ORANGE)
                .build();

        // رسم بياني دائري (Donut) للمقبوضات مقابل المدفوعات
        Tile cashFlowTile = TileBuilder.create()
                .skinType(Tile.SkinType.DONUT_CHART)
                .title("حركة الخزينة")
                .text("المقبوضات vs المصروفات")
                .chartData(
                        new ChartData("مقبوضات", report.getTotalReceiptsToday().doubleValue(), Tile.GREEN),
                        new ChartData("مدفوعات ومصروفات", report.getTotalPaymentsAndExpensesToday().doubleValue(), Tile.RED)
                )
                .backgroundColor(TILE_BACKGROUND)
                .build();

        // بطاقة لإجمالي الخصومات اليوم
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
        GridPane pane = new GridPane();
        pane.setHgap(20);
        pane.setVgap(20);
        pane.setPadding(new Insets(20));
        pane.setBackground(new Background(new BackgroundFill(MAIN_BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));

        // توزيع البطاقات في الشبكة (العمود، الصف)
        // أحجام البطاقات الافتراضية هي 250x250، يمكننا جعل المخططات تأخذ مساحة أكبر
        salesComparisonTile.setPrefSize(520, 250); // بطاقة عريضة

        pane.add(salesTodayTile, 0, 0);
        pane.add(purchasesTodayTile, 1, 0);
        pane.add(salesCountTile, 2, 0);

        pane.add(salesComparisonTile, 0, 1, 2, 1); // تأخذ مساحة عمودين
        pane.add(cashFlowTile, 2, 1);

        pane.add(discountsTile, 0, 2);

        // 4. إعداد وعرض الشاشة
        Scene scene = new Scene(pane, 850, 850);
        primaryStage.setTitle("لوحة المتابعة اليومية - Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // دالة لتوليد بيانات وهمية للتجربة (في الواقع ستجلبها من قاعدة البيانات)
    private DailyDashboardReport getDummyReport() throws DaoException {
        DaoFactory daoFactory = DaoFactory.INSTANCE;
        var connection = new ConnectionToDatabase().getDbConnection().getConnection();
        daoFactory.setConnection(connection);
//        return new DailyDashboardReport(
//                145L, // salesCountToday
//                new BigDecimal("15400.50"), // salesTotalToday
//                new BigDecimal("12300.00"), // salesTotalYesterday
//                new BigDecimal("85000.00"), // salesTotalWeek
//                new BigDecimal("320000.00"), // salesTotalMonth
//                22L, // purchasesCountToday
//                new BigDecimal("5400.00"), // purchasesTotalToday
//                3L, // salesReturnsCountToday
//                new BigDecimal("450.00"), // salesReturnsTotalToday
//                1L, // purchasesReturnsCountToday
//                new BigDecimal("120.00"), // purchasesReturnsTotalToday
//                new BigDecimal("16000.00"), // totalReceiptsToday
//                new BigDecimal("6200.00"), // totalPaymentsAndExpensesToday
//                new BigDecimal("350.00") // totalDiscountsToday
//        );

        return daoFactory.dailyDashboardReportDao().loadAll().getFirst();
    }
}