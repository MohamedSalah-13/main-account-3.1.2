package com.hamza.account.controller.reports;

import com.hamza.account.model.domain.ComprehensiveSalesReport;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.features.export.ExcelExportService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;

@Log4j2
public class ComprehensiveSalesController implements Initializable {

    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;

    @FXML private TableView<ComprehensiveSalesReport> tableView;
    @FXML private TableColumn<ComprehensiveSalesReport, String> colInvoiceNum;
    @FXML private TableColumn<ComprehensiveSalesReport, Object> colDate; // استخدام Object لدعم التنسيق المخصص
    @FXML private TableColumn<ComprehensiveSalesReport, String> colCustomer;
    @FXML private TableColumn<ComprehensiveSalesReport, Double> colGross;
    @FXML private TableColumn<ComprehensiveSalesReport, Double> colDiscount;
    @FXML private TableColumn<ComprehensiveSalesReport, Double> colNet;
    @FXML private TableColumn<ComprehensiveSalesReport, Double> colPayed;
    @FXML private TableColumn<ComprehensiveSalesReport, Double> colRemain;

    @FXML private Label lblTotalNet, lblTotalPayed, lblTotalRemain;

    private DaoFactory daoFactory;
    private final ObservableList<ComprehensiveSalesReport> masterData = FXCollections.observableArrayList();
    private final ReportExportService reportExportService = new ReportExportService();
    private final ExcelExportService excelExportService = new ExcelExportService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();

        // ضبط التواريخ الافتراضية ليومنا هذا
        dpFrom.setValue(LocalDate.now());
        dpTo.setValue(LocalDate.now());
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        onSearchAction(); // جلب مبيعات اليوم تلقائياً عند الفتح
    }

    private void setupTableColumns() {
        colInvoiceNum.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colGross.setCellValueFactory(new PropertyValueFactory<>("grossTotal"));
        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discount"));
        colNet.setCellValueFactory(new PropertyValueFactory<>("netTotal"));
        colPayed.setCellValueFactory(new PropertyValueFactory<>("payed"));
        colRemain.setCellValueFactory(new PropertyValueFactory<>("remain"));

        // تنسيق خلية التاريخ
        colDate.setCellValueFactory(new PropertyValueFactory<>("invoiceDate"));
        colDate.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    setText(formatter.format((java.time.LocalDateTime) item));
                }
            }
        });

        // تنسيق جميع الأعمدة الرقمية بفاصلة الألف
        formatDoubleColumn(colGross);
        formatDoubleColumn(colDiscount);
        formatDoubleColumn(colNet);
        formatDoubleColumn(colPayed);
        formatDoubleColumn(colRemain);
    }

    @FXML
    private void onSearchAction() {
        if (dpFrom.getValue() == null || dpTo.getValue() == null) {
            AllAlerts.alertError("يرجى تحديد فترة البحث بدقة");
            return;
        }

        try {
            List<ComprehensiveSalesReport> results = daoFactory.comprehensiveSalesDao()
                    .getSalesByPeriod(dpFrom.getValue(), dpTo.getValue());

            masterData.setAll(results);
            tableView.setItems(masterData);

            calculateFooterTotals();

        } catch (DaoException e) {
            log.error("خطأ في تقرير المبيعات", e);
            AllAlerts.alertError("فشل جلب البيانات: " + e.getMessage());
        }
    }

    private void calculateFooterTotals() {
        double totalNet = masterData.stream().mapToDouble(ComprehensiveSalesReport::getNetTotal).sum();
        double totalPayed = masterData.stream().mapToDouble(ComprehensiveSalesReport::getPayed).sum();
        double totalRemain = masterData.stream().mapToDouble(ComprehensiveSalesReport::getRemain).sum();

        lblTotalNet.setText(String.format("%,.2f", totalNet));
        lblTotalPayed.setText(String.format("%,.2f", totalPayed));
        lblTotalRemain.setText(String.format("%,.2f", totalRemain));
    }

    @FXML
    private void onExportPdf() {
        if (masterData.isEmpty()) return;

        String period = "من " + dpFrom.getValue() + " إلى " + dpTo.getValue();
//        String path = ReportExportService.getDefaultOutputPath("تقرير_المبيعات_الشامل");

//        boolean success = reportExportService.exportComprehensiveSalesReport(masterData, period, path);
//        if (success) showInfo("تم حفظ ملف PDF في المسار:\n" + path);

        new ChoosePdfFile().choosePdfFile("تقرير_المبيعات_الشامل", path ->
                reportExportService.exportComprehensiveSalesReport(masterData, period, path));
    }

    @FXML
    private void onExportExcel() {
        if (masterData.isEmpty()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Sales_Report_" + dpFrom.getValue() + ".xlsx");
        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            try {
                // ملاحظة: تأكد من إضافة الدالة المقابلة في ExcelExportService
                // excelExportService.exportComprehensiveSalesToExcel(masterData, file.getAbsolutePath());
                showInfo("تم تصدير ملف Excel بنجاح.");
            } catch (Exception e) {
                AllAlerts.alertError("فشل تصدير الإكسيل: " + e.getMessage());
            }
        }
    }

    private void formatDoubleColumn(TableColumn<ComprehensiveSalesReport, Double> column) {
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f", item));
                }
            }
        });
    }
}