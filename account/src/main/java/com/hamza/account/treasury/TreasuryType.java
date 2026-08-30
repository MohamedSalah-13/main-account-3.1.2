package com.hamza.account.treasury;

import java.util.Arrays;

/**
 * What kind of vessel a treasury is.
 * <p>
 * A wallet (فودافون كاش، انستاباي) is not a new entity in this schema and must not
 * become one: it is a row in {@code treasury} like any other, and every document
 * already carries a {@code treasury_id}. The type is what lets the screens tell the
 * three apart - an icon, an order in the pickers, and later the transfer fee a
 * wallet charges and a drawer does not.
 * <p>
 * The stored value is the enum name, checked by {@code treasury_type_chk} in
 * {@code V20__treasury_types.sql}. An unknown value from the database is an error
 * with the value in the message, not a silent fallback: a row nobody can classify
 * would otherwise be presented as cash.
 */
public enum TreasuryType {

    CASH("treasury.type.cash", "fas-cash-register"),
    WALLET("treasury.type.wallet", "fas-mobile-alt"),
    BANK("treasury.type.bank", "fas-university");

    private final String labelKey;
    private final String iconLiteral;

    TreasuryType(String labelKey, String iconLiteral) {
        this.labelKey = labelKey;
        this.iconLiteral = iconLiteral;
    }

    /** i18n key of the Arabic name shown to the user - never the literal itself. */
    public String labelKey() {
        return labelKey;
    }

    /** Ikonli literal, per §5 of {@code docs/new-code-rules.md} (no image streams). */
    public String iconLiteral() {
        return iconLiteral;
    }

    /** The value written to {@code treasury.treasury_type}. */
    public String code() {
        return name();
    }

    public static TreasuryType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return CASH;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown treasury type: " + code));
    }
}
