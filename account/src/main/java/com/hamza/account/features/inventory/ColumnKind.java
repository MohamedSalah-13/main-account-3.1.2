package com.hamza.account.features.inventory;

import java.util.Locale;

/**
 * What a column holds, and therefore how it is written and lined up.
 * <p>
 * Quantities carry three decimals because that is what the schema stores
 * ({@code DECIMAL(14,3)}) and a business selling by the metre or the kilo needs
 * them; money carries two. Both are written with thousands separators and Western
 * digits: the default locale here is Arabic, and Arabic-Indic digits in a money
 * column do not line up with the figures the rest of the application prints.
 */
public enum ColumnKind {

    TEXT(0),
    QUANTITY(3),
    MONEY(2);

    private final int decimals;

    ColumnKind(int decimals) {
        this.decimals = decimals;
    }

    public int decimals() {
        return decimals;
    }

    /** Whether the column is a number, and so right-aligned. */
    public boolean numeric() {
        return this != TEXT;
    }

    public String format(double value) {
        return String.format(Locale.US, "%,." + decimals + "f", value);
    }
}
