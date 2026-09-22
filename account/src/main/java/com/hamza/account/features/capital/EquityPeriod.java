package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One year, month or week of the owner's equity: what came in and went out of it, and where it
 * stood when the period closed.
 */
public record EquityPeriod(LocalDate start, String label, BigDecimal paidIn, BigDecimal drawn, BigDecimal profit,
                           BigDecimal closing) {

    public BigDecimal change() {
        return paidIn.subtract(drawn).add(profit);
    }
}
