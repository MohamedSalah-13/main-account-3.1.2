package com.hamza.account.type;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Display names for the tables the audit log records against.
 * <p>
 * {@code STOCKS} and {@code STOCK_TRANSFER} survive the removal of multi-warehouse
 * support on purpose: {@code AuditLogDao} resolves each row with
 * {@link #valueOf(String)} against the table name stored at the time, so dropping a
 * constant would make every historical audit entry for that table throw instead of
 * displaying. Nothing writes them any more - they exist to keep the past readable.
 * <p>
 * Unlike {@code UsersType} and {@code ExpensesType}, nothing matches a database value
 * back against {@link #getType()} - the audit log looks a row up by {@link #name()},
 * not by this label - so the label is free to track the active language, and does:
 * each constant's property is refreshed when {@link LanguageManager}'s locale changes.
 */
public enum TableType {

    COMPANY("setting.company"),
    CUSTOM("customers"),
    CUSTOMER_ACC("cuAcc"),
    EMPLOYEES("employees"),
    EXPENSES("expenses"),
    GROUP_MAIN("mainGroup"),
    GROUP_SUB("subGroup"),
    ITEMS("items"),
    STOCKS("stock"),
    SUPPLIERS("suppliers"),
    SUPPLIERS_ACCOUNT("supAcc"),
    TOTAL_BUY("setting.total.purchase"),
    TOTAL_BUY_RETURN("RePur"),
    TOTAL_SALES("setting.total.sales"),
    TOTAL_SALES_RETURN("ReSal"),
    TREASURY("setting.treasury"),
    UNITS("setting.units"),
    USERS("users"),
    STOCK_TRANSFER("setting.store.transfers"),
    TREASURY_TRANSFERS("setting.treasury.transfers"),
    ITEMS_UNITS("table.type.items_units"),
    NOT_USED("table.type.not_used");

    private final String key;
    private final StringProperty type;

    TableType(String key) {
        this.key = key;
        this.type = new SimpleStringProperty(LanguageManager.getInstance().getString(key));
    }

    static {
        LanguageManager.getInstance().currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            for (TableType value : values()) {
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
