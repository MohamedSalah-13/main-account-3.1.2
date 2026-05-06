package com.hamza.account.model.domain;

import java.math.BigDecimal;

@lombok.Data
public class TopSellingItem {
    private String itemName;
    private BigDecimal totalQuantity;
    private BigDecimal averagePrice;

    public TopSellingItem(String itemName, BigDecimal totalQuantity, BigDecimal averagePrice) {
        this.itemName = itemName;
        this.totalQuantity = totalQuantity;
        this.averagePrice = averagePrice;
    }

}