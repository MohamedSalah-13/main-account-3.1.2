package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The rate a currency has on a given day: the latest one dated on it or before it
 * (docs/currency-plan.md ق-٣), with the one it replaced beside it.
 * <p>
 * A currency with no rate on or before the day has no {@code RateInForce} at all - never a zero and
 * never a one. Either would convert an amount into a figure that looks like an answer.
 *
 * @param effectiveDate the day this rate has applied from, which may be long before the day asked about
 * @param previousRate  the rate it replaced, or {@code null} for the first one recorded
 */
public record RateInForce(int currencyId, LocalDate effectiveDate, BigDecimal rate, BigDecimal previousRate) {

    public RateInForce {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(rate, "rate");
    }

    /**
     * How far this rate moved from the one before it, in percent to two places - or {@code null} when
     * there was none before it: a change against nothing is not a number.
     */
    public BigDecimal changePercent() {
        return CurrencyConverter.changePercent(rate, previousRate);
    }

    /** How old the rate is on {@code day}, in days - what the screen warns about when it grows. */
    public long ageOn(LocalDate day) {
        return Math.max(0, day.toEpochDay() - effectiveDate.toEpochDay());
    }
}
