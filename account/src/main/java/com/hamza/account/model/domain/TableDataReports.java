package com.hamza.account.model.domain;

import com.hamza.controlsfx.table.ColumnData;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class TableDataReports {

    private double report_year;
    private double report_month;

    @ColumnData(titleName = "الشهر")
    private String report_month_name;
    @ColumnData(titleName = "المشتريات")
    private double purchase;
    @ColumnData(titleName = "خصم المشتريات")
    private double purchases_discount;
    @ColumnData(titleName = "المبيعات")
    private double sales;
    @ColumnData(titleName = "خصم المبيعات")
    private double sales_discount;
    @ColumnData(titleName = "مرتجع المشتريات")
    private double purchases_return;
    @ColumnData(titleName = "خصم مرتجع المشتريات")
    private double purchases_return_discount;
    @ColumnData(titleName = "مرتجع المبيعات")
    private double sales_return;
    @ColumnData(titleName = "خصم مرتجع المبيعات")
    private double sales_return_discount;
    @ColumnData(titleName = "المصروفات")
    private double expense;
    @ColumnData(titleName = "الربح")
    private double profit;
    @ColumnData(titleName = "الربح نسبة")
    private String profitPercent;
}
