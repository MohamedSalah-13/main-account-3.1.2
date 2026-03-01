package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.util.function.ToDoubleFunction;

public interface PurchaseSalesInterface {

    default int id(BasePurchasesAndSales t1) {
        return t1.getId();
    }

    default int numItem(BasePurchasesAndSales t1) {
        return t1.getItems().getId();
    }

    default double getQuantity(BasePurchasesAndSales t1) {
        return t1.getQuantity();
    }

    default double getPrice(BasePurchasesAndSales t1) {
        return t1.getPrice();
    }

    default double getDiscount(BasePurchasesAndSales t1) {
        return t1.getDiscount();
    }

    default double getTotal(BasePurchasesAndSales t1) {
        return t1.getTotal();
    }

    default UnitsModel getUnitsType(BasePurchasesAndSales t1) {
        return t1.getUnitsType();
    }

    default ItemsModel getItems(BasePurchasesAndSales t1) {
        return t1.getItems();
    }

    default ToDoubleFunction<BasePurchasesAndSales> getTotalBuy() {
        return BasePurchasesAndSales::getTotal_buy_price;
    }

}
