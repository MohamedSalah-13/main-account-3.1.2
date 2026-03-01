package com.hamza.account.controller.model_print;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class ModelPrintInvoice {

    private String name_item;
    private String barcode;
    private String type;
    private double price;
    private double quantity;
    private double total;
    private double discount;
    private double total_amount;

}
