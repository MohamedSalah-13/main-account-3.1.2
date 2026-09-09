package com.hamza.account.document;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;

/**
 * What a whole totals search adds up to, read from one aggregate statement.
 *
 * <p>The two derived figures are computed here rather than in SQL because they are
 * subtractions of numbers already present, and doing them once keeps the definition in
 * one place: what is still owed is the net less what was settled, exactly as
 * {@code DocumentLedgerEffect} defines it.</p>
 *
 * @param count    how many documents matched
 * @param total    the sum of the gross totals
 * @param discount the sum of the discounts
 * @param paid     the sum of what each document settled in cash
 * @param profit   the sum of the per-document profits, zero for a purchase family
 */
public record TotalsSummaryRow(int count, BigDecimal total, BigDecimal discount,
                               BigDecimal paid, BigDecimal profit) {

    public static final TotalsSummaryRow EMPTY = new TotalsSummaryRow(
            0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public TotalsSummaryRow {
        total = money(total);
        discount = money(discount);
        paid = money(paid);
        profit = money(profit);
    }

    /** Net of every discount - the figure a document's account effect is measured against. */
    public BigDecimal afterDiscount() {
        return MoneyMath.subtract(total, discount);
    }

    /** What the matched documents have still not settled. */
    public BigDecimal remaining() {
        return MoneyMath.subtract(afterDiscount(), paid);
    }

    private static BigDecimal money(BigDecimal value) {
        return MoneyMath.money(value == null ? BigDecimal.ZERO : value);
    }
}
