package com.hamza.account.features.currency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The arithmetic of a conversion, and nothing else: no database, no clock, no rate lookup.
 * <p>
 * <b>A rate is base units per one unit of the currency</b> (docs/currency-plan.md ق-٢), so a foreign
 * amount goes to the base by multiplying and comes back by dividing. The base's own rate is one.
 * <p>
 * <b>Rounding happens once, at the end, half up, to the target currency's places</b> (ق-٥). A conversion
 * between two foreign currencies passes through the base without being rounded there: rounding the
 * pound figure first and then the riyal figure is two roundings, and on a large amount the second one
 * lands a piastre away from the answer a calculator gives. Every intermediate is carried at
 * {@link MathContext#DECIMAL128}, 34 significant digits, which a {@code DECIMAL(20, 10)} rate times any
 * amount this program holds never reaches.
 */
public final class CurrencyConverter {

    private static final MathContext EXACT_ENOUGH = MathContext.DECIMAL128;

    private CurrencyConverter() {
    }

    /** {@code amount} of a currency whose rate is {@code rate}, in the base currency. */
    public static BigDecimal toBase(BigDecimal amount, BigDecimal rate, Currency base) {
        requireRate(rate);
        return base.round(amount.multiply(rate, EXACT_ENOUGH));
    }

    /** {@code amount} of the base currency, in a currency whose rate is {@code rate}. */
    public static BigDecimal fromBase(BigDecimal amount, BigDecimal rate, Currency target) {
        requireRate(rate);
        return target.round(amount.divide(rate, EXACT_ENOUGH));
    }

    /**
     * {@code amount} of a currency whose rate is {@code fromRate}, in {@code target} whose rate is
     * {@code toRate} - through the base, rounded once.
     */
    public static BigDecimal convert(BigDecimal amount, BigDecimal fromRate, BigDecimal toRate, Currency target) {
        requireRate(fromRate);
        requireRate(toRate);
        return target.round(amount.multiply(fromRate, EXACT_ENOUGH).divide(toRate, EXACT_ENOUGH));
    }

    /**
     * The same rate the other way round - how much of the currency one unit of the base buys - to the
     * column's ten places. For display: "the pound at 0.0206 dollars" is what a shop whose base is strong
     * is used to reading, and it is never stored.
     */
    public static BigDecimal inverse(BigDecimal rate) {
        requireRate(rate);
        return BigDecimal.ONE.divide(rate, ExchangeRateRules.SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /**
     * How far {@code rate} moved from {@code previous}, in percent to two places - or {@code null} when
     * there is nothing before it: a change against nothing is not a number. The one definition the rate in
     * force and the history panel both use.
     */
    public static BigDecimal changePercent(BigDecimal rate, BigDecimal previous) {
        if (rate == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return rate.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    private static void requireRate(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("A rate is above zero: " + rate);
        }
    }
}
