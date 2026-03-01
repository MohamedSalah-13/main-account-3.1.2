package com.hamza.controlsfx.type;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static com.hamza.controlsfx.language.ResourceLanguage.getMonths;

public enum ValidateType {

    DAY(getMonths("Day")), MONTH(getMonths("Month")), YEAR(getMonths("Year"));

    private final StringProperty type;

    ValidateType(String type) {
        this.type = new SimpleStringProperty(type);
    }


    public static ValidateType getByType(String type) {
        for (ValidateType type1 : ValidateType.values()) {
            if (type1.getType().equals(type)) {
                return type1;
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
