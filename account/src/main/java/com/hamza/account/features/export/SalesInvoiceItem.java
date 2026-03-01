package com.hamza.account.features.export;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SalesInvoiceItem {
    private String itemName;
    private double quantity;
    private double price;
    private double total;
}
