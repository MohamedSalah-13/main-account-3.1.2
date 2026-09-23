package com.hamza.account.features.currency.online;

import com.hamza.account.features.currency.ExchangeRateRules;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * A source's quote turned into this system's rate, once (docs/currency-plan.md §12).
 * <p>
 * A source asked about the pound says "one pound is 0.020614 dollars"; the rate recorded is "the dollar
 * at 48.5107 pounds" - base units per one unit (ق-٢). The one is the other's reciprocal.
 * <p>
 * <b>Kept to six significant digits, not to a number of places.</b> A reciprocal has as many digits as
 * the division is taken to, and a quote of five significant digits does not support ten; but a fixed
 * number of places is wrong both ways - four places would record the rial against a dinar base as zero,
 * and would record a dollar against a weak base with digits nobody could read. Six significant digits
 * is finer than any till rounds to, and never more than the column's ten places.
 */
public final class OnlineRateMath {

    public static final int SIGNIFICANT_DIGITS = 6;

    private static final MathContext DIGITS = new MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP);

    private OnlineRateMath() {
    }

    /**
     * The rate for a currency of which one unit of the base buys {@code unitsPerBase}, or {@code null}
     * when it cannot be one: no quote, a quote of zero or less, or a rate the column cannot hold - more
     * than {@link ExchangeRateRules#INTEGER_DIGITS} digits before the point, or zero at its places.
     */
    public static BigDecimal rateFrom(BigDecimal unitsPerBase) {
        if (unitsPerBase == null || unitsPerBase.signum() <= 0) {
            return null;
        }
        BigDecimal rate = BigDecimal.ONE.divide(unitsPerBase, DIGITS);
        if (rate.scale() > ExchangeRateRules.SCALE) {
            rate = rate.setScale(ExchangeRateRules.SCALE, RoundingMode.HALF_UP);
        }
        rate = rate.stripTrailingZeros();
        if (rate.signum() <= 0 || rate.precision() - rate.scale() > ExchangeRateRules.INTEGER_DIGITS) {
            return null;
        }
        // 4.2E+4 is 42000; a rate written with an exponent would reach the screen and the column that way.
        return rate.scale() < 0 ? rate.setScale(0) : rate;
    }
}
