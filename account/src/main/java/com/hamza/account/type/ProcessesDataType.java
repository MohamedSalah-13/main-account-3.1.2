package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Only ever compared by identity, never by its label, so the label is free to track
 * the active language: each constant's property is refreshed when
 * {@link LanguageManager}'s locale changes.
 */
public enum ProcessesDataType {

    DELETE("delete"),
    INSERT("insert"),
    UPDATE("update");

    private final String key;
    private final StringProperty type;

    ProcessesDataType(String key) {
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (ProcessesDataType value : values()) {
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
