package com.hamza.account.controller.invoice;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility holder for invoice field keys used across the application.
 * Backward-compatible string constants are provided, and a type-safe enum {@link Key}
 * centralizes the definitions and allows iteration via {@link #allKeys()}.
 */
public final class ShowInvoiceNameData {

    // Backward-compatible constants
    public static final String ID = Key.ID.key();
    public static final String NAME = Key.NAME.key();
    public static final String DATE = Key.DATE.key();
    public static final String STOCK = Key.STOCK.key();
    public static final String PAID = Key.PAID.key();
    public static final String DISCOUNT = Key.DISCOUNT.key();
    public static final String TOTAL = Key.TOTAL.key();
    public static final String REST = Key.REST.key();
    public static final String TYPE = Key.TYPE.key();
    public static final String DATE_INSERT = Key.DATE_INSERT.key();

    private ShowInvoiceNameData() {
        // Utility class: prevent instantiation
    }

    /**
     * Returns an unmodifiable set of all key strings.
     */
    public static Set<String> allKeys() {
        return EnumSet.allOf(Key.class).stream().map(Key::key).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Type-safe set of known invoice field keys.
     */
    public enum Key {
        ID("id"),
        NAME("name"),
        DATE("date"),
        STOCK("stock"),
        PAID("paid"),
        DISCOUNT("discount"),
        TOTAL("total"),
        REST("rest"),
        TYPE("type"),
        DATE_INSERT("date_insert");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
