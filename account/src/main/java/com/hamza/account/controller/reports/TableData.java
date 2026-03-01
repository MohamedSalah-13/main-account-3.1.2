package com.hamza.account.controller.reports;

import com.hamza.controlsfx.table.ColumnData;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TableData {

    @ColumnData(titleName = "الشهر")
    private String name;
    @ColumnData(titleName = "المشتريات")
    private double purchase;
    @ColumnData(titleName = "خصم المشتريات")
    private double discountPurchase;
    @ColumnData(titleName = "المبيعات")
    private double sales;
    @ColumnData(titleName = "خصم المبيعات")
    private double discountSales;
    @ColumnData(titleName = "مرتجع المشتريات")
    private double purchaseRe;
    @ColumnData(titleName = "خصم مرتجع المشتريات")
    private double discountPurchaseRe;
    @ColumnData(titleName = "مرتجع المبيعات")
    private double salesRe;
    @ColumnData(titleName = "خصم مرتجع المبيعات")
    private double discountSalesRe;
    @ColumnData(titleName = "المصروفات")
    private double expense;
    @ColumnData(titleName = "الربح")
    private double profit;
    @ColumnData(titleName = "الربح نسبة")
    private String profitPercent;
}
