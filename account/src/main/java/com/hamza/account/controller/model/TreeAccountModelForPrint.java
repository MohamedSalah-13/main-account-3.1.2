package com.hamza.account.controller.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TreeAccountModelForPrint {

    private int id;
    private String name;
    private String date;
    private double purchase;
    private double paid;
    private double amount;
    private String notes;


}
