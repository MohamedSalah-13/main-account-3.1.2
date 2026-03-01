package com.hamza.account.controller.invoice;

import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.features.export.SalesInvoiceData;
import com.hamza.account.features.export.SalesInvoiceItem;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.stream.Collectors;

/**
 * مساعد تصدير الفواتير
 */
@Log4j2
public class InvoiceExportHelper {

    private final ReportExportService exportService;

    public InvoiceExportHelper() {
        this.exportService = new ReportExportService();
    }

    /**
     * تصدير فاتورة مبيعات
     */
    public boolean exportSalesInvoiceToPdf(
            String companyName,
            String companyAddress,
            String companyPhone,
            int invoiceNumber,
            String invoiceDate,
            String customerName,
            List<PurchaseItem> items,
            double discount,
            double tax,
            String notes,
            String outputPath) {

        List<SalesInvoiceItem> invoiceItems = items.stream()
                .map(item -> SalesInvoiceItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .total(item.getTotal())
                        .build())
                .collect(Collectors.toList());

        double subtotal = items.stream()
                .mapToDouble(PurchaseItem::getTotal)
                .sum();

        double total = subtotal - discount + tax;

        SalesInvoiceData invoiceData = SalesInvoiceData.builder()
                .companyName(companyName)
                .companyAddress(companyAddress)
                .companyPhone(companyPhone)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .customerName(customerName)
                .items(invoiceItems)
                .subtotal(subtotal)
                .discount(discount)
                .tax(tax)
                .total(total)
                .notes(notes)
                .build();

        return exportService.exportSalesInvoice(invoiceData, outputPath);
    }

    // Model للأصناف
    @lombok.Data
    public static class PurchaseItem {
        private String itemName;
        private double quantity;
        private double price;
        private double total;
    }
}