package com.hamza.account.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class ExpensesSalary {

    private int id;
    private int employee_id;
    private int expense_details_id;

}
