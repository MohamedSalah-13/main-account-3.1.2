package com.hamza.account.model.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class Sales_Package {

    private int id;
    private int sales_id;
    private int items_id;
    private int unit_id;
    private double quantity;
    private double selling_price;
    private double buying_price;
    private double total_sales;
    private double total_buying;
    private double total_profit;
    private double discount;
    private double unit_value;
    private LocalDate expiration_date;
}


