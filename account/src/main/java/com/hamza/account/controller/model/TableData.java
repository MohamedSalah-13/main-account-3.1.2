package com.hamza.account.controller.model;

import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Data;

@Data
public class TableData {

    @ColumnData(titleName = NamesTables.NAME)
    private String username;
    @ColumnData(titleName = NamesTables.PURCHASE)
    private double totalPurchase;
    @ColumnData(titleName = NamesTables.PURCHASE_RETURN)
    private double totalPurchaseReturn;
    @ColumnData(titleName = NamesTables.SALES)
    private double totalSales;
    @ColumnData(titleName = NamesTables.SALES_RETURN)
    private double totalSalesReturn;
    @ColumnData(titleName = "مدفوع المبيعات")
    private double totalReceipt;
    @ColumnData(titleName = "مدفوع المشتريات")
    private double totalPaid;
    @ColumnData(titleName = "حساب المبيعات")
    private double account_customer;
    @ColumnData(titleName = "حساب المشتريات")
    private double account_supplier;
    @ColumnData(titleName = NamesTables.DAMAGED)
    private double totalDamaged;
    @ColumnData(titleName = NamesTables.OTHER_EXPENSES)
    private double totalExpense;
    private double total_profit;
    private double total_cost;
    private double total_balance;
    private double total_deposit;
    private double total_deposit_expense;

}
