package com.hamza.account.model.domain;

import lombok.Data;

@Data
public class DailyItemSales {
    private String itemName;
    private double price;
    private double quantity;
    private double total;
    private String invoiceNumber;
    private String invoiceTime; // لعرض وقت البيع (ساعة:دقيقة)
}
