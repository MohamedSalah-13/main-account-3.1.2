package com.hamza.account.type;


import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UsersType {

    ADMIN(1, Setting_Language.ADMIN), MANAGER(2, Setting_Language.MANAGER),
    EMPLOYEE(3, Setting_Language.EMPLOYEE), DELEGATE(4, Setting_Language.DELEGATE);

    private final IntegerProperty id;
    private final StringProperty type;

    UsersType(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static UsersType getUserTypeById(int id) {
        for (UsersType userType : UsersType.values()) {
            if (userType.getId() == id) {
                return userType;
            }
        }
        return null;

    }

    public static UsersType getUserTypeByType(String type) {
        for (UsersType userType : UsersType.values()) {
            if (userType.getType().equals(type)) {
                return userType;
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
