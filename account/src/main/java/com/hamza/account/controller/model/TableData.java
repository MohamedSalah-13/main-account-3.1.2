package com.hamza.account.controller.model;

import com.hamza.account.config.NamesTables;
import lombok.Data;

@Data
public class TableData {

    private String username;
    private double totalPurchase;
    private double totalPurchaseReturn;
    private double totalSales;
    private double totalSalesReturn;
    private double totalReceipt;
    private double totalPaid;
    private double account_customer;
    private double account_supplier;
    private double totalDamaged;
    private double totalExpense;
    private double total_profit;
    private double total_cost;
    private double total_balance;
    private double total_deposit;
    private double total_deposit_expense;

}
