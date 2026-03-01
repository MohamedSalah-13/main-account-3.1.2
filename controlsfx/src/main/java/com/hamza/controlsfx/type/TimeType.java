package com.hamza.controlsfx.type;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import static com.hamza.controlsfx.language.ResourceLanguage.getMonths;

public enum TimeType {

    HOUR(getMonths("hour")),
    MINUTE(getMonths("Minute")),
    SECOND(getMonths("Second"));

    private final StringProperty type;

    TimeType(String type) {
        this.type = new SimpleStringProperty(type);
    }

    public static TimeType getByType(String type) {
        for (TimeType type1 : TimeType.values()) {
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
