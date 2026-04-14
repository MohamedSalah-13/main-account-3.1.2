package com.hamza.account.controller.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class UpdateInvoiceRow {

    public static void updateData(BasePurchasesAndSales selectedItem) {
        double price = selectedItem.getPrice();
        double quantity = selectedItem.getQuantity();
        double discount = selectedItem.getDiscount();
        double round = roundToTwoDecimalPlaces((quantity * price));
        selectedItem.setTotal(round);
        selectedItem.setTotal_after_discount(roundToTwoDecimalPlaces(round - discount));
    }
}
