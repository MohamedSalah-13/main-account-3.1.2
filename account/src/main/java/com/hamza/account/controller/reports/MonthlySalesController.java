package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.model.domain.MonthlySalesViewModel;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.*;

@Log4j2
@RequiredArgsConstructor
public class MonthlySalesController implements Initializable {

    private final ObservableList<MonthlySalesViewModel> salesDataList = FXCollections.observableArrayList();
    private final ReportExportService reportExportService = new ReportExportService();
    private MonthlySalesInterface monthlySalesInterface;
    @FXML
    private Label title;
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

    public void loadData(DaoFactory daoFactory, MonthlySalesInterface monthlySalesInterface) {
        this.monthlySalesInterface = monthlySalesInterface;
        title.setText(monthlySalesInterface.reportTitle());
        chartSales.setTitle(monthlySalesInterface.chartTitle());
        MonthlySalesViewDao salesDao = monthlySalesInterface.getMonthlySalesViewDao(daoFactory);
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
            var lang = LanguageManager.getInstance();
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.jan"), getDoubleValue(yearData.getJanuary())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.feb"), getDoubleValue(yearData.getFebruary())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.mar"), getDoubleValue(yearData.getMarch())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.apr"), getDoubleValue(yearData.getApril())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.may"), getDoubleValue(yearData.getMay())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.jun"), getDoubleValue(yearData.getJune())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.jul"), getDoubleValue(yearData.getJuly())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.aug"), getDoubleValue(yearData.getAugust())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.sep"), getDoubleValue(yearData.getSeptember())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.oct"), getDoubleValue(yearData.getOctober())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.nov"), getDoubleValue(yearData.getNovember())));
            series.getData().add(new XYChart.Data<>(lang.getString("report.month.dec"), getDoubleValue(yearData.getDecember())));
            chartSales.getData().add(series);
        }
    }

    private double getDoubleValue(BigDecimal value) {
        return (value != null) ? value.doubleValue() : 0.0;
    }

    @FXML
    private void onExportPdf() {
        if (salesDataList.isEmpty()) {
            showWarning(LanguageManager.getInstance().getString("party.error.no.data.export"));
            return;
        }
        var s = monthlySalesInterface.reportName();
        new ChoosePdfFile().choosePdfFile(s + ".pdf", path ->
                reportExportService.exportMonthlyTotalsReport(
                        salesDataList,
                        monthlySalesInterface.reportTitle()
                        , getChartImageBytes(), path
                ));
    }

    @FXML
    private void onExportExcel() {
        if (salesDataList.isEmpty()) {
            showWarning(LanguageManager.getInstance().getString("party.error.no.data.export"));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(LanguageManager.getInstance().getString("report.dialog.save.excel.title"));
        fileChooser.setInitialFileName(monthlySalesInterface.reportName() + ".xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(tableSales.getScene().getWindow());

        if (file != null) {
            try {
                ExcelExportService excelService = new ExcelExportService();
                excelService.exportMonthlySalesToExcel(salesDataList, file.getAbsolutePath());
                showInfo(LanguageManager.getInstance().getString("report.monthly.sales.excel.success"));
            } catch (Exception e) {
                com.hamza.controlsfx.alert.AllAlerts.handleError(LanguageManager.getInstance().getString("report.error.export.monthly.sales.title"), e);
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

}
