package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Persisted and read back by {@link #getId()}, never by {@link #getType()} - so the
 * label is free to track the active language: each constant's property is refreshed
 * when {@link LanguageManager}'s locale changes.
 */
public enum InvoiceType {

    /**
     * Represents a type of invoice with a specific identifier and description for cash transactions.
     */
    CASH(1, "cash"),
    /**
     * Represents the deferred payment invoice type.
     * It is one of the constants of the InvoiceType enum.
     */
    DEFER(2, "defer");

    private final IntegerProperty id;
    private final String key;
    private final StringProperty type;

    InvoiceType(int id, String key) {
        this.id = new SimpleIntegerProperty(id);
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (InvoiceType value : values()) {
                value.type.set(LanguageManager.getInstance().getString(value.key));
            }
        });
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
