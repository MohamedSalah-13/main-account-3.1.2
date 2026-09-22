package com.hamza.account.features.capital;

import java.math.BigDecimal;

/**
 * Every figure the business started the program with, as one line of equity.
 *
 * @param customers what customers owed at the start - an asset
 * @param suppliers what the business owed suppliers at the start - a liability, so it is subtracted
 * @param stock     the opening stock at today's buy prices - a valuation, not a cost
 */
public record BroughtForward(BigDecimal treasuries, BigDecimal customers, BigDecimal suppliers, BigDecimal stock) {

    public static final BroughtForward NONE =
            new BroughtForward(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public BigDecimal total() {
        return treasuries.add(customers).subtract(suppliers).add(stock);
    }
}
