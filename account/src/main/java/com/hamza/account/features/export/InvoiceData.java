package com.hamza.account.features.export;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * نموذج بيانات الفاتورة للتصدير
 */
@Data
@Builder
public class InvoiceData {
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private String invoiceType;
    private int invoiceNumber;
    private String invoiceDate;
    private String customerName;
    private List<InvoiceItem> items;
    private double subtotal;
    private double discount;
    private double tax;
    private double total;
    private String notes;
}

/**
 * نموذج صنف في الفاتورة
 */
@Data
@Builder
class InvoiceItem {
    private String itemName;
    private double quantity;
    private double price;
    private double total;
}