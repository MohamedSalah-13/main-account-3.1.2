package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ExcelExportService;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory; // افترضت وجوده بناءً على ملفاتك السابقة
import com.hamza.account.model.domain.ItemSalesRank;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;

@Log4j2
public class ItemSalesRankController implements Initializable {

    private final ReportExportService reportExportService = new ReportExportService();
    private final ExcelExportService excelExportService = new ExcelExportService();
    private final ObservableList<ItemSalesRank> allData = FXCollections.observableArrayList();
    // --- عناصر التحكم في الواجهة (FXML) ---
    @FXML
    private ComboBox<Integer> comboYear;
    @FXML
    private ComboBox<String> comboMonth; // سيحتوي على الشهور أو "كل السنة"
    @FXML
    private PieChart pieChartBestSellers;
    @FXML
    private TableView<ItemSalesRank> tableView;
    @FXML
    private TableColumn<ItemSalesRank, String> colItemName;
    @FXML
    private TableColumn<ItemSalesRank, Double> colTotalQty;
    @FXML
    private TableColumn<ItemSalesRank, Double> colTotalAmount;
    @FXML
    private TableColumn<ItemSalesRank, Double> colTotalProfit;
    private DaoFactory daoFactory; // للوصول لقاعدة البيانات

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupFilters();
    }

    // لتمرير الـ DaoFactory عند فتح الشاشة
    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        loadAvailableYears();
    }

    private void setupTableColumns() {
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colTotalQty.setCellValueFactory(new PropertyValueFactory<>("totalQty"));
        colTotalAmount.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        colTotalProfit.setCellValueFactory(new PropertyValueFactory<>("totalProfit"));

        // تنسيق الأرقام لتظهر بفاصلة عشرية
        formatDoubleColumn(colTotalQty);
        formatDoubleColumn(colTotalAmount);
        formatDoubleColumn(colTotalProfit);
    }

    private void setupFilters() {
        // إضافة الشهور للقائمة (0 تعني كل السنة)
        comboMonth.getItems().addAll(
                "كل السنة", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        );
        comboMonth.getSelectionModel().selectFirst(); // اختيار "كل السنة" كافتراضي
    }

    private void loadAvailableYears() {
        // هنا يمكنك جلب السنوات من قاعدة البيانات باستخدام استعلام الـ UNION الذي صنعناه سابقاً
        // لغرض المثال، سأضع سنوات افتراضية
        var listYear = daoFactory.totalsPurchaseDao().getListYear();
        comboYear.getItems().addAll(listYear);
        comboYear.getSelectionModel().selectLast();
    }

    @FXML
    private void searchAction() {
        if (comboYear.getValue() == null) {
            AllAlerts.alertError("من فضلك حدد السنة أولاً");
            return;
        }

        int selectedYear = comboYear.getValue();
        int selectedMonthIndex = comboMonth.getSelectionModel().getSelectedIndex(); // 0 = كل السنة، 1 = يناير ...

        try {
            List<ItemSalesRank> result;
            // إذا اختار "كل السنة"، نستدعي الاستعلام السنوي، وإلا نستدعي الاستعلام الشهري
            if (selectedMonthIndex == 0) {
                result = daoFactory.itemSalesRankDao().getBestSellersByYear(selectedYear);
            } else {
                result = daoFactory.itemSalesRankDao().getBestSellersByMonth(selectedYear, selectedMonthIndex);
            }

            allData.setAll(result);
            tableView.setItems(allData);
            updatePieChart(result);

        } catch (DaoException e) {
            log.error("خطأ في جلب بيانات الأصناف الأكثر مبيعاً", e);
            AllAlerts.alertError("حدث خطأ أثناء جلب البيانات: " + e.getMessage());
        }
    }

    // داخل ItemSalesRankController.java

    private void updatePieChart(List<ItemSalesRank> data) {
        pieChartBestSellers.getData().clear();

        // نأخذ أعلى 10 أصناف فقط للرسم البياني حتى لا يصبح مزدحماً جداً
        int limit = Math.min(data.size(), 10);

        for (int i = 0; i < limit; i++) {
            ItemSalesRank item = data.get(i);
            // إضافة الصنف للرسم البياني (الاسم + الكمية المباعة)
            PieChart.Data slice = new PieChart.Data(item.getItemName() + " (" + item.getTotalQty() + ")", item.getTotalQty());
            pieChartBestSellers.getData().add(slice);
        }
    }

    // دالة مساعدة لتنسيق الأرقام في الجدول
    private void formatDoubleColumn(TableColumn<ItemSalesRank, Double> column) {
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

    @FXML
    private void onExportPdf() {
        if (tableView.getItems().isEmpty()) {
            AllAlerts.alertError("لا توجد بيانات لتصديرها");
            return;
        }

        new ChoosePdfFile().choosePdfFile("تقرير حركة الأصناف لسنة " + comboYear.getValue(), path -> {
            // 1. أخذ لقطة (Snapshot) من الـ PieChart
            byte[] chartImage = null;
            try {
                javafx.scene.image.WritableImage image = pieChartBestSellers.snapshot(null, null);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", out);
                chartImage = out.toByteArray();
            } catch (java.io.IOException e) {
                log.error("خطأ في تحويل الرسم البياني لصورة", e);
                return false;
            }

            return reportExportService.exportItemSalesRankReport(
                    tableView.getItems(),
                    "تقرير حركة الأصناف لسنة " + comboYear.getValue(),
                    path,
                    chartImage
            );
        });
    }

    @FXML
    private void onExportExcel() {
        if (tableView.getItems().isEmpty()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("Best_Sellers_" + comboYear.getValue() + ".xlsx");
        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
            try {
                excelExportService.exportItemSalesToExcel(tableView.getItems(), file.getAbsolutePath());
                showInfo("تم تصدير ملف Excel بنجاح");
            } catch (Exception e) {
                AllAlerts.alertError("فشل التصدير: " + e.getMessage());
            }
        }
    }

}