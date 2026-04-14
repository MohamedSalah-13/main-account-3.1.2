package com.hamza.account.controller.model;

import com.hamza.account.model.domain.UnitsModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintPurchaseWithName {
    private int num;
    private double quantity;
    private double price;
    private double discount;
    private double total;
    private UnitsModel unitsType;
    private String itemName;
    private String name;
    private String date;

}
