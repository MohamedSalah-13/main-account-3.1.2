package com.hamza.account.model.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class TableDataReports {

    private double report_year;
    private double report_month;

    private String report_month_name;
    private double purchase;
    private double purchases_discount;
    private double sales;
    private double sales_discount;
    private double purchases_return;
    private double purchases_return_discount;
    private double sales_return;
    private double sales_return_discount;
    private double expense;
    private double profit;
    private String profitPercent;
}
