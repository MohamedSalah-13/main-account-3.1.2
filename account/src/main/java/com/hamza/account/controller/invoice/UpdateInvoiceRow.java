package com.hamza.account.controller.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;

public class UpdateInvoiceRow {

    public static void updateData(BasePurchasesAndSales selectedItem) {
        double price = selectedItem.getPrice();
        double quantity = selectedItem.getQuantity();
        double discount = selectedItem.getDiscount();
        var total = MoneyMath.multiply(quantity, price);
        selectedItem.setTotal(MoneyMath.asDouble(total));
        selectedItem.setTotal_after_discount(MoneyMath.asDouble(
                MoneyMath.subtract(total, MoneyMath.decimal(discount))));
    }
}
