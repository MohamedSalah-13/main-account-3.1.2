package com.hamza.account.features.export;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SalesInvoiceData {
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private int invoiceNumber;
    private String invoiceDate;
    private String customerName;
    private List<SalesInvoiceItem> items;
    private double subtotal;
    private double discount;
    private double tax;
    private double total;
    private String notes;
}
