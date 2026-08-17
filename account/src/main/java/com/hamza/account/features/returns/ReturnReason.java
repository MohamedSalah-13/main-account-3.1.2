package com.hamza.account.features.returns;

import com.hamza.controlsfx.language.LanguageManager;

/**
 * Why a document was returned. Stored in {@code return_reason} (added by
 * {@code V16__return_source.sql}, {@code VARCHAR(32)} - every name below fits it with
 * room to spare) and nullable there for the same reason the column is: no return
 * entry screen asks for one yet.
 * <p>
 * A plain enum, not a database-backed catalog: the set of reasons a shop returns goods
 * for does not vary per install the way a permission or a unit does, and a new one is
 * a constant here plus two translation lines, not a migration.
 */
public enum ReturnReason {

    DAMAGED("return.reason.damaged"),
    WRONG_ITEM("return.reason.wrong_item"),
    CUSTOMER_CHANGED_MIND("return.reason.customer_changed_mind"),
    QUALITY_ISSUE("return.reason.quality_issue"),
    OTHER("return.reason.other");

    private final String key;

    ReturnReason(String key) {
        this.key = key;
    }

    /** The label in the active language - resolved live, unlike {@code InvoiceType}'s cached property, since nothing here binds it to a control yet. */
    public String label() {
        return LanguageManager.getInstance().getString(key);
    }

    /** The value written to {@code return_reason}; {@link #valueOf} reads it back. */
    public String storedValue() {
        return name();
    }

    public static ReturnReason fromStoredValue(String value) {
        return value == null || value.isBlank() ? null : ReturnReason.valueOf(value);
    }
}
