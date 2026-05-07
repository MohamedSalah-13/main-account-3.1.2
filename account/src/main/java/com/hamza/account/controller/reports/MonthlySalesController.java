package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.model.domain.MonthlySalesViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.util.List;
import java.util.ResourceBundle;

public class MonthlySalesController implements Initializable {

    // ==========================================
    // 1. تعريف عناصر الواجهة (FXML Injections)
    // ==========================================

    @FXML
    private TableView<MonthlySalesViewModel> tableSales;

    @FXML
    private TableColumn<MonthlySalesViewModel, Integer> colYear;
    @FXML
    private TableColumn<MonthlySalesViewModel, BigDecimal> colJan, colFeb, colMar, colApr,
            colMay, colJun, colJul, colAug,
            colSep, colOct, colNov, colDec, colTotal;

    @FXML
    private BarChart<String, Number> chartSales;

    // اتصال قاعدة البيانات والـ DAO
    private Connection connection; // تأكد من تمرير الاتصال لهذا الكلاس عند فتح الشاشة
    private MonthlySalesViewDao salesDao;

    // قائمة البيانات التي سيتم ربطها بالجدول
    private ObservableList<MonthlySalesViewModel> salesDataList = FXCollections.observableArrayList();
    private ReportExportService reportExportService = new ReportExportService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // تهيئة أعمدة الجدول
        setupTableColumns();

        // ملاحظة: يُفضل استدعاء loadData() بعد تمرير الـ Connection من الشاشة الرئيسية
    }

    // ==========================================
    // 2. إعداد أعمدة الجدول
    // ==========================================
    private void setupTableColumns() {
        // ربط الأعمدة بالخصائص (Properties) الموجودة في الـ Model
        colYear.setCellValueFactory(new PropertyValueFactory<>("salesYear"));

        colJan.setCellValueFactory(new PropertyValueFactory<>("january"));
        colFeb.setCellValueFactory(new PropertyValueFactory<>("february"));
        colMar.setCellValueFactory(new PropertyValueFactory<>("march"));
        colApr.setCellValueFactory(new PropertyValueFactory<>("april"));
        colMay.setCellValueFactory(new PropertyValueFactory<>("may"));
        colJun.setCellValueFactory(new PropertyValueFactory<>("june"));
        colJul.setCellValueFactory(new PropertyValueFactory<>("july"));
        colAug.setCellValueFactory(new PropertyValueFactory<>("august"));
        colSep.setCellValueFactory(new PropertyValueFactory<>("september"));
        colOct.setCellValueFactory(new PropertyValueFactory<>("october"));
        colNov.setCellValueFactory(new PropertyValueFactory<>("november"));
        colDec.setCellValueFactory(new PropertyValueFactory<>("december"));

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalYearlySales"));
    }

    // ==========================================
    // 3. جلب البيانات من قاعدة البيانات
    // ==========================================
    public void loadData(Connection conn) {
        this.connection = conn;
        this.salesDao = new MonthlySalesViewDao(connection);

        try {
            // مسح البيانات القديمة
            salesDataList.clear();

            // جلب البيانات من الـ DAO
            List<MonthlySalesViewModel> data = salesDao.loadAll();
            salesDataList.addAll(data);

            // وضع البيانات في الجدول
            tableSales.setItems(salesDataList);

            // رسم الرسم البياني بناءً على البيانات الجديدة
            populateChart(data);

        } catch (Exception e) {
            e.printStackTrace();
            // يمكنك هنا إظهار رسالة خطأ للمستخدم (Alert)
        }
    }

    // ==========================================
    // 4. تعبئة الرسم البياني (BarChart)
    // ==========================================
    private void populateChart(List<MonthlySalesViewModel> data) {
        chartSales.getData().clear(); // تنظيف الرسم البياني القديم

        // كل سنة ستمثل "سلسلة بيانات" (Series) مختلفة بلون مختلف في الرسم البياني
        for (MonthlySalesViewModel yearData : data) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(String.valueOf(yearData.getSalesYear())); // اسم السلسلة هو السنة (مثال: 2025)

            // إضافة بيانات الشهور للسلسلة الحالية (نحول BigDecimal إلى Double ليقبله الرسم البياني)
            series.getData().add(new XYChart.Data<>("يناير", getDoubleValue(yearData.getJanuary())));
            series.getData().add(new XYChart.Data<>("فبراير", getDoubleValue(yearData.getFebruary())));
            series.getData().add(new XYChart.Data<>("مارس", getDoubleValue(yearData.getMarch())));
            series.getData().add(new XYChart.Data<>("أبريل", getDoubleValue(yearData.getApril())));
            series.getData().add(new XYChart.Data<>("مايو", getDoubleValue(yearData.getMay())));
            series.getData().add(new XYChart.Data<>("يونيو", getDoubleValue(yearData.getJune())));
            series.getData().add(new XYChart.Data<>("يوليو", getDoubleValue(yearData.getJuly())));
            series.getData().add(new XYChart.Data<>("أغسطس", getDoubleValue(yearData.getAugust())));
            series.getData().add(new XYChart.Data<>("سبتمبر", getDoubleValue(yearData.getSeptember())));
            series.getData().add(new XYChart.Data<>("أكتوبر", getDoubleValue(yearData.getOctober())));
            series.getData().add(new XYChart.Data<>("نوفمبر", getDoubleValue(yearData.getNovember())));
            series.getData().add(new XYChart.Data<>("ديسمبر", getDoubleValue(yearData.getDecember())));

            // إضافة السلسلة (السنة) إلى الرسم البياني
            chartSales.getData().add(series);
        }
    }

    // دالة مساعدة لتحويل BigDecimal إلى Double للرسم البياني وتجنب القيم الفارغة (Null)
    private double getDoubleValue(BigDecimal value) {
        return (value != null) ? value.doubleValue() : 0.0;
    }

    // دالة تصدير PDF
    @FXML
    private void onExportPdf() {
        if (salesDataList.isEmpty()) {
            showWarning("لا توجد بيانات لتصديرها");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName("تقرير_المبيعات_السنوي.pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(tableSales.getScene().getWindow());

        if (file != null) {
            // اختيار مكان الحفظ
//        String defaultPath = ReportExportService.getDefaultOutputPath("تقرير_المبيعات_السنوي");
            String defaultPath = file.getAbsolutePath();

            boolean success = reportExportService.exportMonthlyTotalsReport(
                    salesDataList,
                    "تقرير إجمالي المبيعات الشهرية لكل سنة",
                    defaultPath
            );

            if (success) {
                showInfo("تم تصدير ملف PDF بنجاح في المسار: " + defaultPath);
                // فتح الملف تلقائياً بعد التصدير
                try {
                    java.awt.Desktop.getDesktop().open(new File(defaultPath));
                } catch (Exception e) {
                }
            } else {
                showError("فشل في تصدير ملف PDF");
            }
        }
    }

    // دالة تصدير Excel
    @FXML
    private void onExportExcel() {
        if (salesDataList.isEmpty()) {
            showWarning("لا توجد بيانات لتصديرها");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ ملف Excel");
        fileChooser.setInitialFileName("Monthly_Sales_Report.csv"); // تصدير CSV لسهولة التوافق
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(tableSales.getScene().getWindow());

        if (file != null) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                // كتابة العناوين
                writer.println("Year,Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct,Nov,Dec,Total");

                // كتابة البيانات
                for (MonthlySalesViewModel row : salesDataList) {
                    writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                            row.getSalesYear(),
                            row.getJanuary(), row.getFebruary(), row.getMarch(), row.getApril(),
                            row.getMay(), row.getJune(), row.getJuly(), row.getAugust(),
                            row.getSeptember(), row.getOctober(), row.getNovember(), row.getDecember(),
                            row.getTotalYearlySales()
                    );
                }
                showInfo("تم تصدير ملف Excel بنجاح");
            } catch (Exception e) {
                showError("خطأ أثناء تصدير Excel: " + e.getMessage());
            }
        }
    }

    // دوال مساعدة للرسائل
    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).show();
    }

    private void showWarning(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).show();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).show();
    }
}
