package com.hamza.controlsfx.type;

import com.hamza.controlsfx.language.Error_Text_Show;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


public enum ActivityType {

    ACTIVE(Error_Text_Show.ACTIVATED),
    NOT_ACTIVE(Error_Text_Show.IN_ACTIVE);

    private final StringProperty type;

    ActivityType(String type) {
        this.type = new SimpleStringProperty(type);
    }


    public static ActivityType getByType(String type) {
        for (ActivityType userType : ActivityType.values()) {
            if (userType.getType().equals(type)) {
                return userType;
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
