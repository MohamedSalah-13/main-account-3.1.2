package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One year, month or week of the owner's equity: what came in and went out of it, and where it
 * stood when the period closed.
 */
public record EquityPeriod(LocalDate start, String label, BigDecimal paidIn, BigDecimal drawn, BigDecimal profit,
                           BigDecimal closing) {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal change() {
        return paidIn.subtract(drawn).add(profit);
    }

    /** Where the period opened: its close less what moved inside it. */
    public BigDecimal opening() {
        return closing.subtract(change());
    }

    /**
     * The mean of the period's opening and closing equity - the definition decided in
     * {@code docs/reports-plan.md} §13.1. Capital paid in on the last day counts as if it had been
     * there half the period; that is the price of the ordinary definition, and it was chosen knowingly.
     */
    public BigDecimal averageEquity() {
        return opening().add(closing).divide(TWO, 2, RoundingMode.HALF_UP);
    }

    /**
     * The period's profit as a percentage of its average equity, or empty when the average is zero or
     * less: a return on nothing, or on a deficit, is not a number, and a zero there would read as
     * "earned nothing" - the {@code PartyTrendSummary} rule.
     */
    public Optional<BigDecimal> returnOnEquity() {
        BigDecimal average = averageEquity();
        if (average.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(profit.multiply(HUNDRED).divide(average, 2, RoundingMode.HALF_UP));
    }
}
