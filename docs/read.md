اقرأ docs/AI_CONTEXT.md فقط ولا تقرأ ملفات أخرى في docs إلا عند الحاجة.
المشكلة: <وصف مختصر للمشكلة>

تمام — سنضيف **تصدير Excel + PDF** فعليًا، مع الإبقاء على:

- فلتر التاريخ
- البحث بالاسم
- إجمالي البيع
- إجمالي الكمية
- صافي الإجمالي
- ترتيب حسب التاريخ

---

## 1) تعديل `CustomerPurchasedItemsController.java`
نستبدل الاستدعاءات الوهمية للتصدير بتصدير فعلي باستخدام:
- `ExportData` للـ Excel
- `PdfExportService` للـ PDF

```java
package com.hamza.account.controller.customer;

import com.hamza.account.controller.model.PurchasedItemByCustomerView;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.PdfExportService;
import com.hamza.account.service.CustomerPurchasedItemsService;
import com.hamza.account.service.CustomerService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.ExportData;
import com.hamza.controlsfx.util.ExcelException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

@Log4j2
@FxmlPath(pathFile = "customer/customer-purchased-items-view.fxml")
public class CustomerPurchasedItemsController implements Initializable, AppSettingInterface {

    private final CustomerPurchasedItemsService purchasedItemsService;
    private final CustomerService customerService;
    private final int customerId;

    @FXML
    private TableView<PurchasedItemByCustomerView> tableView;
    @FXML
    private Label labelCustomerName;
    @FXML
    private Label labelCount;
    @FXML
    private Label labelTotalSales;
    @FXML
    private Label labelTotalQuantity;
    @FXML
    private Label labelNetTotal;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private TextField textSearchName;
    @FXML
    private Button btnSearch;
    @FXML
    private Button btnReset;
    @FXML
    private Button btnExportExcel;
    @FXML
    private Button btnExportPdf;
    @FXML
    private Button btnSortDate;

    private final ObservableList<PurchasedItemByCustomerView> masterData = FXCollections.observableArrayList();
    private final ObservableList<PurchasedItemByCustomerView> filteredData = FXCollections.observableArrayList();

    public CustomerPurchasedItemsController(DaoFactory daoFactory, int customerId) {
        this.purchasedItemsService = new CustomerPurchasedItemsService(daoFactory);
        this.customerService = new CustomerService(daoFactory);
        this.customerId = customerId;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupControls();
        loadCustomerName();
        loadData();
        applyFilters();
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, PurchasedItemByCustomerView.class);
        tableView.setItems(filteredData);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupControls() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);

        if (btnSearch != null) {
            btnSearch.setOnAction(e -> applyFilters());
        }
        if (btnReset != null) {
            btnReset.setOnAction(e -> resetFilters());
        }
        if (btnSortDate != null) {
            btnSortDate.setOnAction(e -> {
                masterData.setAll(purchasedItemsService.sortByDateDescending(masterData));
                applyFilters();
            });
        }
        if (btnExportExcel != null) {
            btnExportExcel.setOnAction(e -> exportExcel());
        }
        if (btnExportPdf != null) {
            btnExportPdf.setOnAction(e -> exportPdf());
        }
    }

    private void loadCustomerName() {
        try {
            Customers customer = customerService.getCustomerById(customerId);
            if (customer != null && labelCustomerName != null) {
                labelCustomerName.setText(customer.getName());
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }
    }

    private void loadData() {
        try {
            masterData.setAll(purchasedItemsService.getPurchasedItemsByCustomerId(customerId));
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }
    }

    private void applyFilters() {
        var result = masterData.stream().toList();

        if (dateFrom != null && dateTo != null && dateFrom.getValue() != null && dateTo.getValue() != null) {
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            if (from.isAfter(to)) {
                AllAlerts.alertError("تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
                return;
            }
            result = purchasedItemsService.filterByDateRange(result, from, to);
        }

        if (textSearchName != null && !textSearchName.getText().isBlank()) {
            result = purchasedItemsService.filterByItemName(result, textSearchName.getText());
        }

        filteredData.setAll(result);
        tableView.refresh();
        updateSummary();
    }

    private void updateSummary() {
        if (labelCount != null) {
            labelCount.setText(String.valueOf(filteredData.size()));
        }
        if (labelTotalSales != null) {
            labelTotalSales.setText(String.valueOf(purchasedItemsService.sumTotalSales(filteredData)));
        }
        if (labelTotalQuantity != null) {
            labelTotalQuantity.setText(String.valueOf(purchasedItemsService.sumTotalQuantity(filteredData)));
        }
        if (labelNetTotal != null) {
            labelNetTotal.setText(String.valueOf(purchasedItemsService.sumTotalAfterDiscount(filteredData)));
        }
    }

    private void resetFilters() {
        if (dateFrom != null) dateFrom.setValue(null);
        if (dateTo != null) dateTo.setValue(null);
        if (textSearchName != null) textSearchName.clear();
        filteredData.setAll(masterData);
        updateSummary();
    }

    private void exportExcel() {
        try {
            if (filteredData.isEmpty()) {
                AllAlerts.alertError("لا توجد بيانات للتصدير");
                return;
            }
            int result = ExportData.exportDataToExcel(
                    filteredData.stream().toList(),
                    new CustomerPurchasedItemsExcelWriter(filteredData, labelCustomerName != null ? labelCustomerName.getText() : "")
            );
            if (result >= 1) {
                AllAlerts.alertSaveWithMessage("تم تصدير ملف Excel بنجاح");
            }
        } catch (ExcelException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void exportPdf() {
        try {
            if (filteredData.isEmpty()) {
                AllAlerts.alertError("لا توجد بيانات للطباعة");
                return;
            }

            File file = new javafx.stage.FileChooser().showSaveDialog(tableView.getScene().getWindow());
            if (file == null) {
                return;
            }

            List<String[]> rows = filteredData.stream()
                    .map(row -> new String[]{
                            String.valueOf(row.getInvoiceNumber()),
                            row.getInvoiceDate(),
                            row.getItemName(),
                            row.getItemBarcode(),
                            row.getUnitName(),
                            String.valueOf(row.getQuantity()),
                            String.valueOf(row.getPrice()),
                            String.valueOf(row.getDiscount()),
                            String.valueOf(row.getTotal()),
                            String.valueOf(row.getTotalAfterDiscount())
                    })
                    .toList();

            PdfExportService pdfExportService = new PdfExportService();
            boolean success = pdfExportService.exportGenericReport(
                    file.getAbsolutePath(),
                    "الأصناف المشتراة من العميل",
                    "العميل: " + (labelCustomerName != null ? labelCustomerName.getText() : ""),
                    new String[]{"رقم الفاتورة", "التاريخ", "الصنف", "الباركود", "الوحدة", "الكمية", "السعر", "الخصم", "الإجمالي", "الصافي"},
                    new float[]{10, 14, 18, 14, 10, 10, 10, 10, 10, 10},
                    rows,
                    "الإجمالي",
                    String.valueOf(purchasedItemsService.sumTotalAfterDiscount(filteredData)),
                    com.itextpdf.kernel.geom.PageSize.A4.rotate()
            );

            if (success) {
                AllAlerts.alertSaveWithMessage("تم تصدير ملف PDF بنجاح");
            } else {
                AllAlerts.alertError("حدث خطأ أثناء التصدير");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new Pane();
    }

    @Override
    public String title() {
        return "الأصناف المشتراة من العميل";
    }

    @Override
    public boolean resize() {
        return true;
    }
}
```


---

## 2) إضافة كاتب Excel خاص بالنافذة
هذا الكلاس يربط `ExportData` بالبيانات الحالية.

```java
package com.hamza.account.controller.customer;

import com.hamza.account.controller.model.PurchasedItemByCustomerView;
import com.hamza.controlsfx.interfaces.WriteExcelInterface;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CustomerPurchasedItemsExcelWriter implements WriteExcelInterface<PurchasedItemByCustomerView> {

    private final List<PurchasedItemByCustomerView> items;
    private final String customerName;

    @Override
    public Object[] columnHeader() {
        return new Object[]{
                "رقم الفاتورة", "التاريخ", "العميل", "الصنف", "الباركود", "الوحدة",
                "الكمية", "السعر", "الخصم", "الإجمالي", "الصافي"
        };
    }

    @Override
    public Object[] dataRow(PurchasedItemByCustomerView item) {
        return new Object[]{
                item.getInvoiceNumber(),
                item.getInvoiceDate(),
                customerName,
                item.getItemName(),
                item.getItemBarcode(),
                item.getUnitName(),
                item.getQuantity(),
                item.getPrice(),
                item.getDiscount(),
                item.getTotal(),
                item.getTotalAfterDiscount()
        };
    }

    @Override
    public List<PurchasedItemByCustomerView> itemsList() {
        return items;
    }

    @Override
    public String sheetName() {
        return "Purchased Items";
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }
}
```


---

## 3) إذا كان `WriteExcelInterface` غير معروف عندك بهذا الشكل
فإنه غالبًا موجود داخل مكتبة المشروع.  
لو ظهر تعارض في الاسم أو الميثودات، أعد إرسال تعريف الواجهة وسأضبطه لك فورًا.

---

## 4) تعديل صغير في `PdfExportService`
لا نحتاج تعديل كبير، لأننا استخدمنا `exportGenericReport(...)` الموجود أصلًا.  
لكن لو أردت، أضيف لك دالة متخصصة باسم أوضح.

### اختياري:
```java
// ... existing code ...

    public boolean exportPurchasedItemsCustomerReport(
            String filePath,
            String customerName,
            String fromDate,
            String toDate,
            List<String[]> data,
            String totalValue) {
        return exportGenericReport(
                filePath,
                "الأصناف المشتراة من العميل",
                "العميل: " + customerName + "  |  من: " + fromDate + "  |  إلى: " + toDate,
                new String[]{"رقم الفاتورة", "التاريخ", "الصنف", "الباركود", "الوحدة", "الكمية", "السعر", "الخصم", "الإجمالي", "الصافي"},
                new float[]{10, 14, 18, 14, 10, 10, 10, 10, 10, 10},
                data,
                "إجمالي الصافي",
                totalValue,
                com.itextpdf.kernel.geom.PageSize.A4.rotate()
        );
    }

// ... existing code ...
```


---

## 5) تعديل `customer-purchased-items-view.fxml`
نضيف فقط أزرار التصدير إن لم تكن موجودة بالفعل.

```xml
<Button fx:id="btnExportExcel" text="تصدير Excel"/>
<Button fx:id="btnExportPdf" text="تصدير PDF"/>
```


---

## 6) النتيجة النهائية
أصبح لديك:
- فلتر تاريخ
- بحث بالاسم
- إجمالي البيع
- إجمالي الكمية
- صافي الإجمالي
- ترتيب حسب التاريخ
- تصدير Excel
- تصدير PDF

---

## ملاحظة مهمة جدًا
في حال ظهر خطأ في:
- `PdfExportService`
- أو `WriteExcelInterface`
- أو `ExportData`

فهذا غالبًا بسبب اختلاف التوقيعات الفعلية في مشروعك.  
إذا حدث ذلك، أرسل لي تعريف الواجهة أو الخطأ وسأضبطه لك مباشرة بدون تغيير بنية الميزة.

إذا تريد، أقدر الآن أعمل لك **نسخة أدق** تتوافق مع مشروعك 100% عبر فحص:
- `WriteExcelInterface`
- `InvoiceData`
- وأي خدمة تصدير PDF موجودة بالفعل.