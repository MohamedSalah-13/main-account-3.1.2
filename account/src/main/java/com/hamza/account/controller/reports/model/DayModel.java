package com.hamza.account.controller.reports.model;

import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class DayModel {

    @ColumnData(titleName = NamesTables.DATE)
    private String dateInsert;
    @ColumnData(titleName = NamesTables.SALES)
    private double sales;
    @ColumnData(titleName = NamesTables.CREDITOR)
    private double paid;
}
