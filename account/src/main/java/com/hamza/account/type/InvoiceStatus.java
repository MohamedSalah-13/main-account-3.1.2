package com.hamza.account.type;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum InvoiceStatus {

    OPEN(1, "Opening"),

    CLOSE(2, "Closed");

    private final IntegerProperty id;
    private final StringProperty type;

    InvoiceStatus(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static InvoiceStatus getInvoiceStatusById(int id) {
        for (InvoiceStatus invoiceType : InvoiceStatus.values()) {
            if (invoiceType.getId() == id) {
                return invoiceType;
            }
        }
        return null;
    }

    public static InvoiceStatus getInvoiceStatusByType(String type) {
        for (InvoiceStatus invoiceType : InvoiceStatus.values()) {
            if (invoiceType.getType().equals(type)) {
                return invoiceType;
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
