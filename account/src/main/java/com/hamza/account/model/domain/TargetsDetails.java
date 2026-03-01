package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseTarget;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TargetsDetails extends BaseTarget {

    private int employee_id;
    @ColumnData(titleName = NamesTables.NAME)
    private String employee_name;
    @ColumnData(titleName = "إجمالى المبيعات")
    private double totals_sales_sum;
    @ColumnData(titleName = "إجمالى المرتجعات")
    private double totals_sales_re_sum;
    @ColumnData(titleName = NamesTables.AMOUNT)
    private double totals_amount;
    @ColumnData(titleName = "الهدف")
    private double target;
    @ColumnData(titleName = "السنة")
    private int sales_year;
    @ColumnData(titleName = "الشهر")
    private int sales_month;
    @ColumnData(titleName = "العمولة")
    private double commission_month;

}
