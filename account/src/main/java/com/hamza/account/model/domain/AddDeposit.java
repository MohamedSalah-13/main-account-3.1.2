package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseEntity;
import com.hamza.account.type.OperationType;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class AddDeposit extends BaseEntity {

    @ColumnData(titleName = "amount")
    private double amount;
    @ColumnData(titleName = "date")
    private LocalDate date;
    @ColumnData(titleName = "statement")
    private String Statement;
    private String description_data;
    private OperationType operationType;
    private Treasury treasury;

}
