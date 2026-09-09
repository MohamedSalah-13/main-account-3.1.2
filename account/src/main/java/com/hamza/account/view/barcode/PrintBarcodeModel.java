package com.hamza.account.view.barcode;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.Objects;

/** Mutable row state for the JavaFX print table; deliberately contains no JavaFX properties. */
public final class PrintBarcodeModel {
    private final String barcode;
    private final String name;
    private final BigDecimal price;
    private int quantity;

    public PrintBarcodeModel(String barcode, String name, double price) {
        this(barcode, name, MoneyMath.money(price), 1);
    }

    public PrintBarcodeModel(String barcode, String name, BigDecimal price, int quantity) {
        this.barcode = Objects.requireNonNullElse(barcode, "").trim();
        this.name = Objects.requireNonNullElse(name, "").trim();
        this.price = MoneyMath.money(price == null ? BigDecimal.ZERO : price);
        this.quantity = quantity;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
