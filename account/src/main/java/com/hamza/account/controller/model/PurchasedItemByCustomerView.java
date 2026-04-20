package com.hamza.account.controller.model;

import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchasedItemByCustomerView {
    @ColumnData(titleName = "code")
    private int invoiceNumber;
    @ColumnData(titleName = "date")
    private String invoiceDate;
    private int customerId;
    @ColumnData(titleName = "customerName")
    private String customerName;
    @ColumnData(titleName = "item")
    private int itemId;
    @ColumnData(titleName = "barcode")
    private String itemBarcode;
    @ColumnData(titleName = "name")
    private String itemName;
    @ColumnData(titleName = "unit")
    private String unitName;
    @ColumnData(titleName = "quantity")
    private double quantity;
    @ColumnData(titleName = "price")
    private double price;
    @ColumnData(titleName = "discount")
    private double discount;
    @ColumnData(titleName = "total")
    private double total;
    @ColumnData(titleName = "totalAfterDiscount")
    private double totalAfterDiscount;
}