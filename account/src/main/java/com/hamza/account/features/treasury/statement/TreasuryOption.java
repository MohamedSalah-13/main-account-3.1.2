package com.hamza.account.features.treasury.statement;

/**
 * A treasury the statement can be of. {@code currencyCode} is its currency's code, or {@code null}
 * for a treasury in the base.
 */
public record TreasuryOption(int id, String name, boolean active, String currencyCode) {

    public TreasuryOption(int id, String name, boolean active) {
        this(id, name, active, null);
    }
}
