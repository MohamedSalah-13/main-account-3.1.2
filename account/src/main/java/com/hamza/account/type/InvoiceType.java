package com.hamza.account.type;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum InvoiceType {

    /**
     * Represents a type of invoice with a specific identifier and description for cash transactions.
     */
    CASH(1, Setting_Language.WORD_CASH),
    /**
     * Represents the deferred payment invoice type.
     * It is one of the constants of the InvoiceType enum.
     */
    DEFER(2, Setting_Language.WORD_DEFER);

    private final IntegerProperty id;
    private final StringProperty type;

    InvoiceType(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static InvoiceType getInvoiceTypeById(int id) {
        for (InvoiceType invoiceType : InvoiceType.values()) {
            if (invoiceType.getId() == id) {
                return invoiceType;
            }
        }
        return null;
    }

    public static InvoiceType getInvoiceTypeByType(String type) {
        for (InvoiceType invoiceType : InvoiceType.values()) {
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
