package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * What a recorded rate may be. V80's {@code currency_rate} is the limit: {@code DECIMAL(20, 10)}, a CHECK
 * that the rate is above zero, and one rate per currency per day.
 * <p>
 * <b>A rate with more places than the column holds is refused, not rounded.</b> MySQL would round it on
 * the way in without a word, and the rate stored would then not be the rate the person typed - which is
 * the one thing a rate screen must never do quietly.
 */
public final class ExchangeRateRules {

    /** The column's places after the point. */
    public static final int SCALE = 10;

    /** The column's places before it: {@code DECIMAL(20, 10)} leaves ten. */
    public static final int INTEGER_DIGITS = 10;

    /** {@code currency_rate.notes} is {@code VARCHAR(200)}. */
    public static final int NOTES_MAX = 200;

    private ExchangeRateRules() {
    }

    /**
     * @param currency the currency the rate is for, as stored - {@code null} when there is none
     */
    public static void requireValid(ExchangeRateDraft draft, Currency currency) throws UserValidationException {
        if (currency == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        // The base is worth one of itself by definition. A row saying otherwise would be read by nothing
        // and believed by whoever looked at it.
        if (currency.base()) {
            throw new UserValidationException("currency.rate.error.base");
        }
        if (draft.effectiveDate() == null) {
            throw new UserValidationException("currency.rate.error.date");
        }
        BigDecimal rate = draft.rate();
        if (rate == null || rate.signum() <= 0) {
            throw new UserValidationException("currency.rate.error.positive");
        }
        BigDecimal plain = rate.stripTrailingZeros();
        if (plain.scale() > SCALE) {
            throw new UserValidationException("currency.rate.error.precision");
        }
        if (plain.precision() - plain.scale() > INTEGER_DIGITS) {
            throw new UserValidationException("currency.rate.error.too.large");
        }
        String notes = draft.notes();
        if (notes != null && notes.codePointCount(0, notes.length()) > NOTES_MAX) {
            throw new UserValidationException("currency.rate.error.notes");
        }
    }
}
