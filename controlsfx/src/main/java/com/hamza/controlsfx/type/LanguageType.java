package com.hamza.controlsfx.type;

import com.hamza.controlsfx.language.Error_Text_Show;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum LanguageType {

    AR(Error_Text_Show.ARABIC),
    EN(Error_Text_Show.ENGLISH);

    private final StringProperty type;

    LanguageType(String type) {
        this.type = new SimpleStringProperty(type);
    }

    public static LanguageType getByType(String s) {
        for (LanguageType type : LanguageType.values()) {
            if (type.getType().equals(s)) {
                return type;
            }
        }
        return null;
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
