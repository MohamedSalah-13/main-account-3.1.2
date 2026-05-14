package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
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
    private Integer customerId;      // INT
    private String customerName;    // VARCHAR
    @ColumnData(titleName = NamesTables.ITEM_NAME)
    private String itemName;        // VARCHAR
    @ColumnData(titleName = NamesTables.QUANTITY)
    private BigDecimal quantity;     // DECIMAL(14,3)
    @ColumnData(titleName = NamesTables.SEL_PRICE)
    private BigDecimal sellingPrice; // DECIMAL(14,2)
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate invoiceDate;   // DATE
    @ColumnData(titleName = NamesTables.CODE_INVOICE)
    private Long invoiceNumber;      // BIGINT

}
