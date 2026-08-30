package com.hamza.account.features.treasury;

import java.util.Arrays;

/**
 * What kind of hand-entered movement a row is: ordinary cash, capital paid in by the
 * owner, or the owner's drawings.
 * <p>
 * The distinction is not bookkeeping pedantry, it is the difference between a true
 * profit and a false one. Capital paid in is <b>not income</b> and drawings are
 * <b>not an expense</b>: recorded as either, the treasury still comes out right and
 * the profit - the number the owner actually reads - is wrong by the whole amount.
 * <p>
 * A category implies a direction and cannot contradict it: capital comes in,
 * drawings go out. {@link #requires()} says which, {@code null} for {@link #NORMAL}
 * which allows both. The same rule is a CHECK in {@code V21__treasury_capital.sql} -
 * the service refuses the pair with a sentence the user can read, and the database
 * refuses it whatever reaches it.
 * <p>
 * Neither of the two owner categories reaches the profit and loss report, because
 * that report reads expenses from {@code expenses_details} and never looks at this
 * table. {@code ProfitLossExcludesCapitalTest} holds it to that.
 */
public enum CashCategory {

    NORMAL("treasury.category.normal", null),
    CAPITAL_IN("treasury.category.capital", CashDirection.DEPOSIT),
    OWNER_DRAW("treasury.category.drawings", CashDirection.WITHDRAWAL);

    private final String labelKey;
    private final CashDirection requires;

    CashCategory(String labelKey, CashDirection requires) {
        this.labelKey = labelKey;
        this.requires = requires;
    }

    public String labelKey() {
        return labelKey;
    }

    /** The only direction this category may take, or {@code null} when either will do. */
    public CashDirection requires() {
        return requires;
    }

    /** True for anything that belongs to the owner rather than to the business. */
    public boolean isOwnerEquity() {
        return this != NORMAL;
    }

    public boolean allows(CashDirection direction) {
        return requires == null || requires == direction;
    }

    /** The value stored in {@code treasury_deposit_expenses.category}. */
    public String code() {
        return name();
    }

    /**
     * An unreadable value is an error with the value in the message rather than a
     * silent fall back to NORMAL: a row nobody can classify would otherwise be
     * counted as an ordinary movement and quietly change the owner's equity.
     */
    public static CashCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NORMAL;
        }
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cash category: " + code));
    }
}
