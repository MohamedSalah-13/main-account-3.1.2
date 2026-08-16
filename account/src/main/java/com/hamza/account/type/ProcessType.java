package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Only ever compared by identity ({@code ==}/{@code .equals} against the constant
 * itself, never by its label), so the label is free to track the active language:
 * each constant's property is refreshed when {@link LanguageManager}'s locale changes.
 */
public enum ProcessType {

    PURCHASE("pur"),
    PURCHASE_RETURN("RePur"),
    SALES("sales"),
    SALES_RETURN("ReSal");

    private final String key;
    private final StringProperty type;

    ProcessType(String key) {
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (ProcessType value : values()) {
                value.type.set(LanguageManager.getInstance().getString(value.key));
            }
        });
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }
}
