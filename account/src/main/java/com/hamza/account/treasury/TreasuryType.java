package com.hamza.account.treasury;

import com.hamza.account.config.AppIcon;

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

    CASH("treasury.type.cash", AppIcon.TREASURY_CASH),
    WALLET("treasury.type.wallet", AppIcon.TREASURY_WALLET),
    BANK("treasury.type.bank", AppIcon.TREASURY_BANK);

    private final String labelKey;
    private final AppIcon icon;

    TreasuryType(String labelKey, AppIcon icon) {
        this.labelKey = labelKey;
        this.icon = icon;
    }

    /** i18n key of the Arabic name shown to the user - never the literal itself. */
    public String labelKey() {
        return labelKey;
    }

    /**
     * A semantic {@link AppIcon}, not a raw Ikonli literal: this project ships the
     * Feather pack alone, so a FontAwesome literal would compile happily and render
     * nothing at all.
     */
    public AppIcon icon() {
        return icon;
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
