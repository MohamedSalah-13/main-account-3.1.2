package com.hamza.account.type;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum DiscountType {

    AMOUNT(1, "amount"),

    RATE(2, "rate");

    private final IntegerProperty id;
    private final StringProperty type;

    DiscountType(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static DiscountType getDiscountTypeById(int id) {
        for (DiscountType discountType : DiscountType.values()) {
            if (discountType.getId() == id) {
                return discountType;
            }
        }
        return null;
    }

    public static DiscountType getDiscountTypeByType(String type) {
        for (DiscountType discountType : DiscountType.values()) {
            if (discountType.getType().equals(type)) {
                return discountType;
            }
        }
        return null;
    }

    public int getId() {
        return id.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }
}
