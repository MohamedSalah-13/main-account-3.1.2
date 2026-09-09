package com.hamza.account.features.totals;

import java.math.BigDecimal;

/**
 * One invoice as a printed listing shows it.
 *
 * <p>A plain record rather than the screen's own model, so the page's shape can be built
 * and checked without a JavaFX toolkit - and so {@code features/totals} keeps knowing
 * nothing about {@code BaseTotals}. The controller, which has both, does the mapping.</p>
 *
 * @param profit zero for a purchase family, which has no revenue to earn one on
 */
public record TotalsDocumentRow(int number, String date, String partyName, String paymentType,
                                BigDecimal total, BigDecimal discount, BigDecimal paid,
                                BigDecimal profit) {

    public BigDecimal afterDiscount() {
        return total.subtract(discount);
    }

    public BigDecimal remaining() {
        return afterDiscount().subtract(paid);
    }
}
