package com.hamza.account.model.domain;

import com.hamza.account.model.base.DForColumnTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExpensesDetails extends DForColumnTable {

    private int id;
    private LocalDate localDate;
    private double amount;
    private String notes;

    private Employees employees;
    private Treasury treasuryModel;
    private Expenses expenses;

}
