package com.hamza.account.type;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessType {

    PURCHASE("المشتريات"),
    PURCHASE_RETURN("مرتجع المشتريات"),
    SALES("المبيعات"),
    SALES_RETURN("مرتجع المبيعات");
    private final StringProperty type;

    ProcessType(String type) {
        this.type = new SimpleStringProperty(type);
    }

    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }
}
