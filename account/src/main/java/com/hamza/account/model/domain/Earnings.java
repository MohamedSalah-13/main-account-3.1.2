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
public class Earnings extends DForColumnTable {

    private int id;
    private int code_id;
    private int invoice_type;
    private LocalDate invoice_date;
    private double total;
    private double quantity;
    private double discount;
    private double paid;
    private int stock_id;
    private int delegate_id;
    private int treasury_id;
    private String table_id;
    private double profit;

}
