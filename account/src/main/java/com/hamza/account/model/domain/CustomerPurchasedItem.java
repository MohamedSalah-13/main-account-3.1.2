package com.hamza.account.model.domain;

import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPurchasedItem {
    @ColumnData(titleName = "Customer ID")
    private Integer customerId;      // INT
    @ColumnData(titleName = "Customer Name")
    private String customerName;    // VARCHAR
    @ColumnData(titleName = "Item Name")
    private String itemName;        // VARCHAR
    @ColumnData(titleName = "Quantity")
    private BigDecimal quantity;     // DECIMAL(14,3)
    @ColumnData(titleName = "Selling Price")
    private BigDecimal sellingPrice; // DECIMAL(14,2)
    @ColumnData(titleName = "Invoice Date")
    private LocalDate invoiceDate;   // DATE
    @ColumnData(titleName = "Invoice Number")
    private Long invoiceNumber;      // BIGINT

}
