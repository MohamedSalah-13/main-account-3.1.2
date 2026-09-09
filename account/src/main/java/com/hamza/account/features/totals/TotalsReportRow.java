package com.hamza.account.features.totals;

import java.math.BigDecimal;

/**
 * One line of a grouped totals report.
 *
 * <p>The four reports differ only in what {@code label} names - a party, a day, a month,
 * a delegate or an item - so they share a row rather than each carrying a record of its
 * own. {@code quantity} is filled by the per-item report alone, where the question is how
 * much moved rather than how much was collected; {@code profit} stays zero for a purchase
 * family, which has no revenue to earn one on.</p>
 */
public record TotalsReportRow(String label, int count, BigDecimal quantity, BigDecimal total,
                              BigDecimal discount, BigDecimal paid, BigDecimal profit) {

    public BigDecimal afterDiscount() {
        return total.subtract(discount);
    }

    public BigDecimal remaining() {
        return afterDiscount().subtract(paid);
    }
}
