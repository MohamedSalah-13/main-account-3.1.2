package com.hamza.account.features.expense;

import java.math.BigDecimal;

/**
 * What one heading holds, beside it on the headings screen.
 *
 * @param expenseCount every expense ever filed under it - what a refused delete is about
 * @param totalSince   what those expenses came to since the day the screen asked about
 */
public record ExpenseHeadingUsage(int headingId, int expenseCount, BigDecimal totalSince) {

    public static ExpenseHeadingUsage none(int headingId) {
        return new ExpenseHeadingUsage(headingId, 0, BigDecimal.ZERO);
    }
}
