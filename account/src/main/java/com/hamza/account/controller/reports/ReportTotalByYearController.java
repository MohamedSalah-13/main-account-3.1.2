package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TableDataReports;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "reports/total-year-profit.fxml")
public class ReportTotalByYearController extends ServiceData {

    private final DaoFactory daoFactory;
    private final ReportExportService reportExportService = new ReportExportService();
    private final ExcelExportService excelExportService = new ExcelExportService();
    @FXML
    private TableView<TableDataReports> tableView;
    @FXML
    private ComboBox<Integer> comboYear;
    @FXML
    private Button searchButton, btnPrintPdf, btnExportExcel;

    public ReportTotalByYearController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
    }

    // داخل ReportTotalByYearController.java

    @FXML
    public void initialize() {
        new TableColumnAnnotation().getTable(tableView, TableDataReports.class);
        TableSetting.tableMenuSetting(getClass(), tableView);

        searchButton.setText(Setting_Language.WORD_SEARCH);
        btnPrintPdf.setText(Setting_Language.WORD_PRINT);

        var imageSetting = new Image_Setting();
        searchButton.setGraphic(ImageChoose.createIcon(imageSetting.search));
        btnPrintPdf.setGraphic(ImageChoose.createIcon(imageSetting.print));

        var listYear = totalBuyService.getListYear();
        comboYear.getItems().setAll(listYear);
        comboYear.getSelectionModel().selectFirst();

        searchButton.setOnAction(event -> {
            try {
                searchAction();
            } catch (DaoException e) {
                log.error(e.getMessage(), e);
                AllAlerts.alertError(e.getMessage());
            }
        });

        tableView.getColumns().forEach(column ->
                column.setStyle("-fx-border-color: #135A8DFF; -fx-border-width: 0 0 0 .5;")
        );

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TableDataReports item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    if (item.getReport_month_name().equals(Setting_Language.WORD_TOTAL)) {
                        setStyle("-fx-background-color: #cccc69; -fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 14px;");
                    }
                }
            }
        });

        btnExportExcel.setOnAction(event -> onExportExcelAction());
        btnPrintPdf.setOnAction(event -> onExportPdfAction());

    }

    private void searchAction() throws DaoException {
        if (comboYear.getValue() == null) {
            AllAlerts.alertError("من فضلك حدد السنة");
            return;
        }

        tableView.getItems().clear();
        var tableDataReports = daoFactory.tableDataReportsDao().loadAllById(comboYear.getSelectionModel().getSelectedItem());
        tableView.getItems().setAll(tableDataReports);
    }

    @FXML
    private void onExportPdfAction() {
        if (tableView.getItems().isEmpty()) {
            AllAlerts.alertError("لا توجد بيانات لتصديرها، قم بالبحث أولاً");
            return;
        }

        String path = ReportExportService.getDefaultOutputPath("تقرير_الأرباح_السنوي_" + comboYear.getValue());
        boolean success = reportExportService.exportYearlyComprehensiveReport(
                tableView.getItems(),
                "تقرير الأرباح والمبيعات لسنة " + comboYear.getValue(),
                path
        );

        if (success) {
            AllAlerts.alertSaveWithMessage("تم حفظ ملف PDF بنجاح:\n" + path);
        }
    }

    @FXML
    private void onExportExcelAction() {
        if (tableView.getItems().isEmpty()) {
            AllAlerts.alertError("لا توجد بيانات لتصديرها");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Report_" + comboYear.getValue() + ".xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            try {
                excelExportService.exportYearlyReportToExcel(tableView.getItems(), file.getAbsolutePath());
                AllAlerts.alertSaveWithMessage("تم تصدير ملف Excel بنجاح");
            } catch (IOException e) {
                log.error(e.getMessage());
                AllAlerts.alertError("فشل التصدير: " + e.getMessage());
            }
        }
    }

}

