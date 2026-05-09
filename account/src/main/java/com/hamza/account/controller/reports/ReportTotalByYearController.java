package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TableDataReports;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "reports/total-year-profit.fxml")
public class ReportTotalByYearController extends ServiceData {

    private final List<TableDataReports> allData = new ArrayList<>();
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

    @FXML
    public void initialize() {
        new TableColumnAnnotation().getTable(tableView, TableDataReports.class);
        TableSetting.tableMenuSetting(getClass(), tableView);

        searchButton.setText(Setting_Language.WORD_SEARCH);
//        btnPrintPdf.setText("Export PDF");
//        btnExportExcel.setText("Export Excel");

//        var imageSetting = new Image_Setting();
//        searchButton.setGraphic(ImageChoose.createIcon(imageSetting.search));
//        btnPrintPdf.setGraphic(ImageChoose.createIcon(imageSetting.print));
//        btnExportExcel.setGraphic(ImageChoose.createIcon(imageSetting.export));

        var listYear = totalBuyService.getListYear();
        comboYear.getItems().setAll(listYear);
        comboYear.getSelectionModel().selectFirst();

        searchButton.setOnAction(event -> {
            try {
                searchAction();
            } catch (DaoException e) {
                log.error(e.getMessage(), e);
                showError(e.getMessage());
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
            showWarning("من فضلك حدد السنة");
            return;
        }

        allData.clear();
        tableView.getItems().clear();

        // جلب البيانات من الـ DAO
        var dataFromDb = daoFactory.tableDataReportsDao().loadAllById(comboYear.getSelectionModel().getSelectedItem());

        if (dataFromDb != null && !dataFromDb.isEmpty()) {
            allData.addAll(dataFromDb);

            // --- إضافة صف الإجمالي هنا ---
            TableDataReports totalRow = calculateTotals(allData);
            allData.add(totalRow);
            // ---------------------------

            tableView.setItems(FXCollections.observableArrayList(allData));
        }
    }

    @FXML
    private void onExportPdfAction() {
        if (tableView.getItems().isEmpty()) {
            showWarning("لا توجد بيانات لتصديرها، قم بالبحث أولاً");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName("تقرير_الأرباح_السنوي_" + comboYear.getValue() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
//        String path = ReportExportService.getDefaultOutputPath("تقرير_الأرباح_السنوي_" + comboYear.getValue());
            String path = file.getAbsolutePath();
            boolean success = reportExportService.exportYearlyComprehensiveReport(
                    tableView.getItems(),
                    "تقرير الأرباح والمبيعات لسنة " + comboYear.getValue(),
                    path
            );

            if (success) {
                showInfo("تم تصدير ملف PDF بنجاح في المسار: " + path);
                try {
                    java.awt.Desktop.getDesktop().open(new File(path));
                } catch (Exception e) {
                    log.error("Error opening PDF file: ", e);
                }
            } else {
                showError("فشل في تصدير ملف PDF");
            }
        }
    }

    @FXML
    private void onExportExcelAction() {
        if (tableView.getItems().isEmpty()) {
            showWarning("لا توجد بيانات لتصديرها");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Report_" + comboYear.getValue() + ".xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            try {
                excelExportService.exportYearlyReportToExcel(tableView.getItems(), file.getAbsolutePath());
                showInfo("تم تصدير ملف Excel بنجاح");
            } catch (IOException e) {
                log.error(e.getMessage());
                showError("فشل التصدير: " + e.getMessage());
            }
        }
    }

    private TableDataReports calculateTotals(List<TableDataReports> dataList) {
        TableDataReports totalRow = new TableDataReports();

        // تعيين اسم الشهر ليكون "الإجمالي" ليتم تلوينه
        totalRow.setReport_month_name(Setting_Language.WORD_TOTAL);

        double sumPurchase = 0;
        double sumPurchaseDisc = 0;
        double sumSales = 0;
        double sumSalesDisc = 0;
        double sumPurchaseRet = 0;
        double sumPurchaseRetDisc = 0;
        double sumSalesRet = 0;
        double sumSalesRetDisc = 0;
        double sumExpense = 0;
        double sumProfit = 0;

        for (TableDataReports item : dataList) {
            sumPurchase += item.getPurchase();
            sumPurchaseDisc += item.getPurchases_discount();
            sumSales += item.getSales();
            sumSalesDisc += item.getSales_discount();
            sumPurchaseRet += item.getPurchases_return();
            sumPurchaseRetDisc += item.getPurchases_return_discount();
            sumSalesRet += item.getSales_return();
            sumSalesRetDisc += item.getSales_return_discount();
            sumExpense += item.getExpense();
            sumProfit += item.getProfit();
        }

        // استخدام دالة التقريب round() قبل حفظ الإجمالي في الكائن
        totalRow.setPurchase(round(sumPurchase));
        totalRow.setPurchases_discount(round(sumPurchaseDisc));
        totalRow.setSales(round(sumSales));
        totalRow.setSales_discount(round(sumSalesDisc));
        totalRow.setPurchases_return(round(sumPurchaseRet));
        totalRow.setPurchases_return_discount(round(sumPurchaseRetDisc));
        totalRow.setSales_return(round(sumSalesRet));
        totalRow.setSales_return_discount(round(sumSalesRetDisc));
        totalRow.setExpense(round(sumExpense));
        totalRow.setProfit(round(sumProfit));

        return totalRow;
    }

    /**
     * دالة مساعدة لتقريب الأرقام العشرية إلى أقرب رقمين
     */
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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

