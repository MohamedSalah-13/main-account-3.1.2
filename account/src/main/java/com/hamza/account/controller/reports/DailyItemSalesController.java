package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DailyItemSales;
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
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;
import static com.hamza.account.controller.reports.ErrorReports.showWarning;
import static com.hamza.controlsfx.alert.AllAlerts.alertError;

@Log4j2
public class DailyItemSalesController implements Initializable {

    private final ObservableList<DailyItemSales> allData = FXCollections.observableArrayList();
    // خدمات التصدير
    private final ReportExportService reportExportService = new ReportExportService();
    private final ExcelExportService excelExportService = new ExcelExportService();
    @FXML
    private DatePicker datePicker;
    @FXML
    private TableView<DailyItemSales> tableView;
    @FXML
    private TableColumn<DailyItemSales, String> colItemName;
    @FXML
    private TableColumn<DailyItemSales, Double> colPrice;
    @FXML
    private TableColumn<DailyItemSales, Double> colQuantity;
    @FXML
    private TableColumn<DailyItemSales, Double> colTotal;
    //    @FXML
//    private TableColumn<DailyItemSales, String> colInvoiceNum;
//    @FXML
//    private TableColumn<DailyItemSales, String> colTime;
    @FXML
    private Label lblDayTotal;
    private DaoFactory daoFactory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();

        // تعيين تاريخ اليوم كقيمة افتراضية عند فتح الشاشة
        datePicker.setValue(LocalDate.now());
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        // يمكننا استدعاء دالة البحث تلقائياً لليوم الحالي عند فتح الشاشة
        onSearchAction();
    }

    private void setupTableColumns() {
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
//        colInvoiceNum.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
//        colTime.setCellValueFactory(new PropertyValueFactory<>("invoiceTime"));

        // تنسيق الأرقام لتظهر بفاصلة الألف ورقمين عشريين
        formatDoubleColumn(colPrice);
        formatDoubleColumn(colQuantity);
        formatDoubleColumn(colTotal);
    }

    @FXML
    private void onSearchAction() {
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            alertError("من فضلك اختر التاريخ أولاً");
            return;
        }

        if (daoFactory == null) return;

        try {
            // جلب البيانات من الـ DAO
            List<DailyItemSales> data = daoFactory.dailyItemSalesDao().getDailyItemsReport(selectedDate);
            allData.setAll(data);
            tableView.setItems(allData);

            // حساب الإجمالي وعرضه في الـ Label بالأسفل
            double dayTotal = allData.stream().mapToDouble(DailyItemSales::getTotal).sum();
            lblDayTotal.setText(String.format("%,.2f", dayTotal));

        } catch (DaoException e) {
            log.error("خطأ في جلب بيانات مبيعات اليوم", e);
            alertError("حدث خطأ أثناء جلب البيانات: " + e.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        if (allData.isEmpty()) {
            showWarning("يرجى البحث عن يوم يحتوي على مبيعات أولاً.");
            return;
        }

        String dateStr = datePicker.getValue().toString();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName("تقرير_الأصناف_اليومى_" + dateStr + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            // 2. التصدير
            String path = file.getAbsolutePath();

            boolean success = reportExportService.exportDailyItemSalesReport(
                    allData,
                    dateStr,
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
                alertError("فشل في تصدير ملف PDF");
            }
        }
    }

    @FXML
    private void onExportExcel() {
        if (allData.isEmpty()) {
            showWarning("يرجى البحث عن يوم يحتوي على مبيعات أولاً.");
            return;
        }

        String dateStr = datePicker.getValue().toString();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ تقرير الإكسيل");
        fileChooser.setInitialFileName("Daily_Sales_" + dateStr + ".xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            try {
                // افترضنا أنك أضفت دالة تصدير الإكسيل اليومية في ExcelExportService
                // إذا لم تقم بإضافتها، يجب إضافتها لتعمل هذه الدالة
                excelExportService.exportDailySalesToExcel(allData, file.getAbsolutePath());
                showInfo("تم تصدير ملف Excel بنجاح.");
            } catch (Exception e) {
                log.error("خطأ تصدير إكسيل", e);
                alertError("فشل التصدير: " + e.getMessage());
            }
        }
    }

    /**
     * دالة مساعدة لتنسيق الأعمدة الرقمية (المبالغ والكميات)
     */
    private void formatDoubleColumn(TableColumn<DailyItemSales, Double> column) {
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