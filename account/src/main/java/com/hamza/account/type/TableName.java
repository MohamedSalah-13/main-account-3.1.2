package com.hamza.account.type;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public enum TableName {

    NAMES(1, Setting_Language.WORD_NAME),
    ACCOUNTS(2, Setting_Language.WORD_ACCOUNT),
    TOTALS(3, Setting_Language.TOTAL),
    RETURNS(4, Setting_Language.RETURN);

    private final IntegerProperty id;
    private final StringProperty type;

    TableName(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static TableName getTableNameById(int id) {
        for (TableName invoiceType : TableName.values()) {
            if (invoiceType.getId() == id) {
                return invoiceType;
            }
        }
        return null;
    }

    public static TableName getTableNameByType(String type) {
        for (TableName invoiceType : TableName.values()) {
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
