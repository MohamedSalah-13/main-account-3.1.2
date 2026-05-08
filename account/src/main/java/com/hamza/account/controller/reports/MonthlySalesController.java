package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.model.domain.MonthlySalesViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Log4j2
public class MonthlySalesController implements Initializable {

    private final ObservableList<MonthlySalesViewModel> salesDataList = FXCollections.observableArrayList();
    private final ReportExportService reportExportService = new ReportExportService();
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
    }

    private void setupTableColumns() {
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

    public void loadData(DaoFactory daoFactory) {
        MonthlySalesViewDao salesDao = daoFactory.monthlySalesViewDao();
        try {
            salesDataList.clear();
            List<MonthlySalesViewModel> data = salesDao.loadAll();
            salesDataList.addAll(data);
            tableSales.setItems(salesDataList);
            populateChart(data);

        } catch (Exception e) {
            log.error("Error loading monthly sales data: ", e);
        }
    }

    private void populateChart(List<MonthlySalesViewModel> data) {
        chartSales.getData().clear();
        data.sort((d1, d2) -> Integer.compare(d2.getSalesYear(), d1.getSalesYear()));

        int maxYearsToShow = 5;
        int count = 0;

        for (MonthlySalesViewModel yearData : data) {
            if (count >= maxYearsToShow) {
                break;
            }
            count++;

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
            chartSales.getData().add(series);
        }
    }

    private double getDoubleValue(BigDecimal value) {
        return (value != null) ? value.doubleValue() : 0.0;
    }

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
//        String defaultPath = ReportExportService.getDefaultOutputPath("تقرير_المبيعات_السنوي");
            String defaultPath = file.getAbsolutePath();

            boolean success = reportExportService.exportMonthlyTotalsReport(
                    salesDataList,
                    "تقرير إجمالي المبيعات الشهرية لكل سنة"
                    , getChartImageBytes(), defaultPath
            );

            if (success) {
                showInfo("تم تصدير ملف PDF بنجاح في المسار: " + defaultPath);
                try {
                    java.awt.Desktop.getDesktop().open(new File(defaultPath));
                } catch (Exception e) {
                    log.error("Error opening PDF file: ", e);
                }
            } else {
                showError("فشل في تصدير ملف PDF");
            }
        }
    }

    @FXML
    private void onExportExcel() {
        if (salesDataList.isEmpty()) {
            showWarning("لا توجد بيانات لتصديرها");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ تقرير إكسيل");
        fileChooser.setInitialFileName("تقرير_المبيعات_السنوي.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(tableSales.getScene().getWindow());

        if (file != null) {
            try {
                ExcelExportService excelService = new ExcelExportService();
                excelService.exportMonthlySalesToExcel(salesDataList, file.getAbsolutePath());
                showInfo("تم تصدير ملف Excel بنجاح مع المخطط البياني");
            } catch (Exception e) {
                showError("خطأ أثناء التصدير: " + e.getMessage());
            }
        }
    }

    private byte[] getChartImageBytes() {
        try {
            WritableImage image = chartSales.snapshot(null, null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Error getting chart image bytes: ", e);
            return null;
        }
    }

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
