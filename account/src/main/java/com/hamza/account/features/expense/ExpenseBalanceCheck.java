package com.hamza.account.features.expense;

import java.math.BigDecimal;

/**
 * Whether an expense takes more out of a till than the till holds.
 * <p>
 * <b>A warning, never a refusal</b> - decision م-١ in docs/expenses-plan.md. A withdrawal is refused
 * over the balance, and an expense is not: the balance is derived from what has been entered, and the
 * ordinary morning is yesterday's bills entered before yesterday's takings. Refusing would stop exactly
 * the entry that makes the balance right. The screen asks, and the person paying answers.
 * <p>
 * An edit is judged against the balance <b>with its own stored amount given back</b>, when it stays on
 * the same till: that amount is already out of the balance, and judging 500 against a balance the same
 * 500 has already reduced would warn on every correction of an expense that fitted when it was paid.
 */
public final class ExpenseBalanceCheck {

    private ExpenseBalanceCheck() {
    }

    /**
     * What the till would be short by, or zero when it would not be.
     *
     * @param balance  the till's current derived balance
     * @param amount   what is about to be paid out of it
     * @param released what an edit gives back to this same till before taking {@code amount} - its own
     *                 stored amount, or zero for a new expense or one moving from another till
     */
    public static BigDecimal shortfall(BigDecimal balance, BigDecimal amount, BigDecimal released) {
        BigDecimal available = orZero(balance).add(orZero(released));
        BigDecimal missing = orZero(amount).subtract(available);
        return missing.signum() > 0 ? missing : BigDecimal.ZERO;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
