package com.hamza.account.features.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The figures above the expenses list, over the whole filtered set rather than the page.
 * <p>
 * The list this replaces totalled {@code tableView.getItems()} - the fifty rows a search had returned,
 * or the page showing - under the word "total", which answered no question anybody asks.
 *
 * @param topHeading      the heading the most was spent under, or {@code null} for an empty set
 * @param previousTotal   what the same filter came to over the period of equal length just before, or
 *                        {@code null} when there is no whole period to compare - which is a different
 *                        statement from zero
 */
public record ExpenseSummary(int count,
                             BigDecimal total,
                             String topHeading,
                             BigDecimal topHeadingTotal,
                             BigDecimal previousTotal) {

    public static final ExpenseSummary EMPTY =
            new ExpenseSummary(0, BigDecimal.ZERO, null, BigDecimal.ZERO, null);

    public ExpenseSummary {
        total = total == null ? BigDecimal.ZERO : total;
        topHeadingTotal = topHeadingTotal == null ? BigDecimal.ZERO : topHeadingTotal;
    }

    /** The average expense, or zero for none. Two places, HALF_UP, like every figure here. */
    public BigDecimal average() {
        return count == 0 ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * The change against the previous period, in percent - or {@code null} when there is nothing to
     * divide by. A rise from nothing is not "100%", and printing a number there would be inventing one.
     */
    public BigDecimal changePercent() {
        if (previousTotal == null || previousTotal.signum() == 0) {
            return null;
        }
        return total.subtract(previousTotal)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousTotal, 1, RoundingMode.HALF_UP);
    }

    public ExpenseSummary withPreviousTotal(BigDecimal previous) {
        return new ExpenseSummary(count, total, topHeading, topHeadingTotal, previous);
    }
}
