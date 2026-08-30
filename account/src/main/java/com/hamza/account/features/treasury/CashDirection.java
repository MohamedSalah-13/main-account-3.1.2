package com.hamza.account.features.treasury;

/**
 * Which way a hand-entered cash movement goes.
 * <p>
 * The column is {@code treasury_deposit_expenses.deposit_or_expenses}, a TINYINT
 * with a CHECK on 1 and 2 and no name for either - the meaning lived in a comment in
 * {@code UserShiftDao} and in the {@code IF(deposit_or_expenses = 1, ...)} inside
 * {@code R__views.sql}. Two magic numbers about money is one transposition away from
 * a withdrawal that reads as a deposit, so they are named once here.
 */
public enum CashDirection {

    DEPOSIT(1, "treasury.cash.deposit", "treasury.cash.error.deposit"),
    WITHDRAWAL(2, "treasury.cash.withdrawal", "treasury.cash.error.withdrawal");

    private final int code;
    private final String labelKey;
    private final String failureKey;

    CashDirection(int code, String labelKey, String failureKey) {
        this.code = code;
        this.labelKey = labelKey;
        this.failureKey = failureKey;
    }

    /** The value stored in {@code deposit_or_expenses}. */
    public int code() {
        return code;
    }

    public String labelKey() {
        return labelKey;
    }

    /** The i18n key of "saving this failed", so the screen names the operation. */
    public String failureKey() {
        return failureKey;
    }

    /** Only a withdrawal can empty a treasury, so only a withdrawal is checked against it. */
    public boolean leavesTheTreasury() {
        return this == WITHDRAWAL;
    }

    public static CashDirection fromCode(int code) {
        for (CashDirection direction : values()) {
            if (direction.code == code) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown deposit_or_expenses value: " + code);
    }
}
