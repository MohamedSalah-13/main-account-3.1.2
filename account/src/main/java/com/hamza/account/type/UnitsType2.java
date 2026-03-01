package com.hamza.account.type;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum UnitsType2 {

    GREAT_UNITY(1, "الوحدة الكبرى"), MINOR_UNIT(2, "الوحدة الصغرى");

    private final IntegerProperty id;
    private final StringProperty type;

    UnitsType2(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static UnitsType2 getUnitByName(String s) {
        for (UnitsType2 type : UnitsType2.values()) {
            if (type.getType().equals(s)) {
                return type;
            }
        }
        return null;
    }

    public static UnitsType2 getUnitTypeById(int id) {
        for (UnitsType2 unitsType : UnitsType2.values()) {
            if (unitsType.getId() == id) {
                return unitsType;
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
