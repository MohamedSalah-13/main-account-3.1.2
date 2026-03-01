package com.hamza.account.test.easyedit;

import javafx.beans.property.*;

public class LineItem {

    private final StringProperty desc = new SimpleStringProperty();
    private final DoubleProperty amount = new SimpleDoubleProperty();
    private final IntegerProperty sort = new SimpleIntegerProperty();

    public LineItem(String dsc, double amt, int srt) {
        desc.set(dsc);
        amount.set(amt);
        sort.set(srt);
    }

    public StringProperty descProperty() {
        return desc;
    }

    public DoubleProperty amountProperty() {
        return amount;
    }

    public IntegerProperty sortProperty() {
        return sort;
    }
}