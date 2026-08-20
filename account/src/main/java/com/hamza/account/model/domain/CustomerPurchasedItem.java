package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
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
    private String itemName;        // VARCHAR
    private BigDecimal quantity;     // DECIMAL(14,3)
    private BigDecimal sellingPrice; // DECIMAL(14,2)
    private LocalDate invoiceDate;   // DATE
    private Long invoiceNumber;      // BIGINT

}
