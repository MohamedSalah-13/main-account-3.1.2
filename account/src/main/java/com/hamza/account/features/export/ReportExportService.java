package com.hamza.account.features.export;

import com.hamza.account.model.domain.*;
import com.itextpdf.kernel.geom.PageSize;
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

    // أضف هذه الدالة داخل كلاس ReportExportService.java

    public boolean exportItemSalesRankReport(
            List<ItemSalesRank> data,
            String title,
            String outputPath,
            byte[] chartImage) {

        String[] headers = {"اسم الصنف", "الكمية المباعة", "إجمالي المبيعات", "صافي الربح"};
        float[] columnWidths = {40f, 20f, 20f, 20f}; // اسم الصنف يأخذ مساحة أكبر

        List<String[]> rows = new ArrayList<>();
        for (ItemSalesRank item : data) {
            rows.add(new String[]{
                    item.getItemName(),
                    format(item.getTotalQty()),
                    format(item.getTotalAmount()),
                    format(item.getTotalProfit())
            });
        }

        // تصدير التقرير مع الصورة (الرسم البياني)
        return pdfExportService.exportGenericReport(
                outputPath,
                title,
                "تقرير تحليل مبيعات الأصناف",
                headers,
                columnWidths,
                rows,
                "", "",
                chartImage,
                PageSize.A4 // الوضع الرأسي مناسب هنا لأن الأعمدة قليلة
        );
    }

    /**
     * تنسيق الأرقام
     */
    private String format(double value) {
        return decimalFormat.format(value);
    }

    public boolean exportDailyItemSalesReport(List<DailyItemSales> data, String dateStr, String outputPath) {
        // 1. العناوين بدون رقم الفاتورة
        String[] headers = {"اسم الصنف", "السعر", "الكمية الإجمالية", "المبلغ الإجمالي"};

        // 2. توزيع المساحات ليصبح المجموع 100%
        float[] columnWidths = {40f, 20f, 20f, 20f};

        List<String[]> rows = new ArrayList<>();
        for (DailyItemSales item : data) {
            rows.add(new String[]{
                    item.getItemName(),
                    format(item.getPrice()),
                    format(item.getQuantity()),
                    format(item.getTotal())
            });
        }

        return pdfExportService.exportGenericReport(
                outputPath,
                "تقرير مبيعات الأصناف ليوم: " + dateStr,
                "تفاصيل حركة المبيعات الصادرة",
                headers,
                columnWidths,
                rows,
                "إجمالي المبيعات المختارة",
                calculateTotal(data),null,
                PageSize.A4
        );
    }
    private String calculateTotal(List<DailyItemSales> data) {
        double total = data.stream().mapToDouble(DailyItemSales::getTotal).sum();
        return format(total);
    }

}

