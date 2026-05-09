package com.hamza.account.model.domain;

import lombok.Data;

@Data
public class ItemSalesRank {
    private int itemId;
    private String itemName;
    private double totalQty;
    private double totalAmount;
    private double totalProfit;
    private int salesYear;
    private int salesMonth;
}