package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Nothing matches a database value back against {@link #getType()} - callers look
 * these up by id - so the label is free to track the active language: each constant's
 * property is refreshed when {@link LanguageManager}'s locale changes.
 */
public enum TableName {

    NAMES(1, "name"),
    ACCOUNTS(2, "account"),
    TOTALS(3, "setting.total"),
    RETURNS(4, "setting.return");

    private final IntegerProperty id;
    private final String key;
    private final StringProperty type;

    TableName(int id, String key) {
        this.id = new SimpleIntegerProperty(id);
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (TableName value : values()) {
                value.type.set(LanguageManager.getInstance().getString(value.key));
            }
        });
    }

    public static TableName getTableNameById(int id) {
        for (TableName invoiceType : TableName.values()) {
            if (invoiceType.getId() == id) {
                return invoiceType;
            }
        }
        return null;
    }

    /**
     * The kind a row's {@code information} column names, refused rather than returned
     * null.
     * <p>
     * Both account ledgers used to read that column straight into
     * {@link #getTableNameById} and then call a method on the answer: one of them wrapped
     * it in {@code requireNonNull} and the other did not, so an id outside 1..4 surfaced
     * as a bare {@code NullPointerException} from inside a row mapper, naming nothing.
     * The value is in the message here, which is the only thing that makes it findable in
     * a table of thousands of movements.
     *
     * @throws IllegalArgumentException if no kind carries that id
     */
    public static TableName requireById(int id) {
        TableName tableName = getTableNameById(id);
        if (tableName == null) {
            throw new IllegalArgumentException(
                    LanguageManager.getInstance().getString("type.error.unknown.table.name", id));
        }
        return tableName;
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
