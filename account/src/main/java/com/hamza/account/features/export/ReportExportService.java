package com.hamza.account.features.export;

import com.hamza.account.controller.reports.model.TableTotals;
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
            ObservableList<TableTotals> data,
            String title,
            String outputPath) {

        String[] headers = {
                "البيان", "يناير", "فبراير", "مارس", "أبريل",
                "مايو", "يونيو", "يوليو", "أغسطس",
                "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر", "الإجمالي"
        };

        float[] columnWidths = {
                15f, 7f, 7f, 7f, 7f, 7f, 7f,
                7f, 7f, 7f, 7f, 7f, 7f, 8f
        };

        List<String[]> rows = new ArrayList<>();
        TableTotals totalRow = null;

        for (TableTotals item : data) {
            if (item.getName().equals("الاجمالى") || item.getName().equals("الإجمالي")) {
                totalRow = item;
                continue;
            }

            String[] row = {
                    item.getName(),
                    format(item.getJan()),
                    format(item.getFeb()),
                    format(item.getMar()),
                    format(item.getApril()),
                    format(item.getMay()),
                    format(item.getJun()),
                    format(item.getJuly()),
                    format(item.getAug()),
                    format(item.getSep()),
                    format(item.getOct()),
                    format(item.getNov()),
                    format(item.getDes()),
                    format(item.getTotals())
            };
            rows.add(row);
        }

        String totalValue = totalRow != null ? format(totalRow.getTotals()) : "0.00";

        return pdfExportService.exportGenericReport(
                outputPath,
                title,
                "تقرير المجاميع الشهرية",
                headers,
                columnWidths,
                rows,
                "الإجمالي الكلي",
                totalValue, PageSize.A4.rotate()
        );
    }

    /**
     * تصدير تقرير حسابات العملاء
     */
    public boolean exportCustomerAccountsReport(
            List<CustomerAccountData> data,
            String outputPath) {

        String[] headers = {"#", "اسم العميل", "له", "عليه", "الرصيد"};
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
                format(totalBalance), PageSize.A4
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
