package com.hamza.account.features.capital;

import java.math.BigDecimal;

/**
 * What the owner put into one treasury and took out of it over a period - the row per treasury
 * {@code docs/treasury-plan.md} §4.3 specified and the capital screen never had.
 */
public record CapitalByTreasuryRow(int treasuryId, String treasuryName, BigDecimal paidIn, BigDecimal drawn,
                                   int movements) {

    public BigDecimal net() {
        return paidIn.subtract(drawn);
    }
}
