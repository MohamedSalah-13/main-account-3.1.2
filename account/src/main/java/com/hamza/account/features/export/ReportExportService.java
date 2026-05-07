package com.hamza.account.features.export;

import com.hamza.account.model.domain.MonthlySalesViewModel;
import com.itextpdf.kernel.geom.PageSize;
import javafx.collections.ObservableList;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * خدمة تصدير التقارير المختلفة
 */
@Log4j2
public class ReportExportService {

    private final PdfExportService pdfExportService;
    private final DecimalFormat decimalFormat;

    public ReportExportService() {
        this.pdfExportService = new PdfExportService();
        this.decimalFormat = new DecimalFormat("#,##0.00");
    }

    /**
     * الحصول على مسار الملف مع اسم تلقائي
     */
    public static String getDefaultOutputPath(String reportName) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = reportName + "_" + timestamp + ".pdf";
        return new File("reports", fileName).getAbsolutePath();
    }


    /**
     * تصدير تقرير المجاميع الشهرية
     */
    public boolean exportMonthlyTotalsReport(
            ObservableList<MonthlySalesViewModel> data,
            String title, byte[] chartImageBytes, // الصورة هنا
            String outputPath) {

        String[] headers = {
                "السنة", "يناير", "فبراير", "مارس", "أبريل",
                "مايو", "يونيو", "يوليو", "أغسطس",
                "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر", "الإجمالي"
        };

        // تم تعديل أحجام الأعمدة لتكون نسب مئوية (المجموع = 100)
        // تقليل حجم عمود السنة وتكبير عمود الإجمالي قليلاً
        float[] columnWidths = {
                6f,   // البيان (السنة)
                7f, 7f, 7f, 7f, 7f, 7f, // 6 شهور الأولى
                7f, 7f, 7f, 7f, 7f, 7f, // 6 شهور الأخيرة
                10f   // الإجمالي
        };

        List<String[]> rows = new ArrayList<>();
        double totalRow = 0.0;

        for (MonthlySalesViewModel item : data) {
            String[] row = {
                    String.valueOf(item.getSalesYear()), // تصحيح: طباعة السنة كنص عادي بدلاً من تنسيق مالي
                    format(item.getJanuary().doubleValue()),
                    format(item.getFebruary().doubleValue()),
                    format(item.getMarch().doubleValue()),
                    format(item.getApril().doubleValue()),
                    format(item.getMay().doubleValue()),
                    format(item.getJune().doubleValue()),
                    format(item.getJuly().doubleValue()),
                    format(item.getAugust().doubleValue()),
                    format(item.getSeptember().doubleValue()),
                    format(item.getOctober().doubleValue()),
                    format(item.getNovember().doubleValue()),
                    format(item.getDecember().doubleValue()),
                    format(item.getTotalYearlySales().doubleValue())
            };
            rows.add(row);
            totalRow += item.getTotalYearlySales().doubleValue();
        }

//        String totalValue = totalRow != null ? format(totalRow.getTotalYearlySales().doubleValue()) : "0.00";

        // يتم طباعة التقرير بالعرض (Landscape) باستخدام PageSize.A4.rotate()
        return pdfExportService.exportGenericReport(
                outputPath,
                title,
                "تقرير المجاميع الشهرية",
                headers,
                columnWidths,
                rows,
                "الإجمالي الكلي",
                format(totalRow), chartImageBytes, PageSize.A4.rotate()
        );
    }

    /**
     * تصدير تقرير حسابات العملاء
     */
    public boolean exportCustomerAccountsReport(
            List<CustomerAccountData> data,
            String outputPath) {

        String[] headers = {"#", "التاريخ", "له", "عليه", "الرصيد"};
        float[] columnWidths = {10f, 40f, 16.67f, 16.67f, 16.67f};

        List<String[]> rows = new ArrayList<>();
        int index = 1;
        double totalBalance = 0;

        for (CustomerAccountData item : data) {
            String[] row = {
                    String.valueOf(index++),
                    item.getCustomerName(),
                    format(item.getDebit()),
                    format(item.getCredit()),
                    format(item.getBalance())
            };
            rows.add(row);
            totalBalance += item.getBalance();
        }

        return pdfExportService.exportGenericReport(
                outputPath,
                "تقرير حسابات العملاء",
                null,
                headers,
                columnWidths,
                rows,
                "الرصيد الإجمالي",
                format(totalBalance), null, PageSize.A4
        );
    }

    /**
     * تصدير فاتورة مبيعات
     */
    public boolean exportSalesInvoice(
            SalesInvoiceData invoiceData,
            String outputPath) {

        List<InvoiceItem> items = new ArrayList<>();
        for (var item : invoiceData.getItems()) {
            items.add(InvoiceItem.builder()
                    .itemName(item.getItemName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .total(item.getTotal())
                    .build());
        }

        InvoiceData data = InvoiceData.builder()
                .companyName(invoiceData.getCompanyName())
                .companyAddress(invoiceData.getCompanyAddress())
                .companyPhone(invoiceData.getCompanyPhone())
                .invoiceType("فاتورة مبيعات")
                .invoiceNumber(invoiceData.getInvoiceNumber())
                .invoiceDate(invoiceData.getInvoiceDate())
                .customerName(invoiceData.getCustomerName())
                .items(items)
                .subtotal(invoiceData.getSubtotal())
                .discount(invoiceData.getDiscount())
                .tax(invoiceData.getTax())
                .total(invoiceData.getTotal())
                .notes(invoiceData.getNotes())
                .build();

        return pdfExportService.exportInvoice(data, outputPath, PageSize.A4.rotate());
    }

    /**
     * تنسيق الأرقام
     */
    private String format(double value) {
        return decimalFormat.format(value);
    }
}
