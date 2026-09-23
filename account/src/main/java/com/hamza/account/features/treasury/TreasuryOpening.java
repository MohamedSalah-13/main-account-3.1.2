package com.hamza.account.features.treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * What a treasury's opening balance stores, once its currency is known (V81, docs/currency-plan.md §11).
 * <p>
 * {@code amount} is the opening in the base - the column every balance has always been measured from.
 * For a treasury in a foreign currency, {@code foreign} is the opening in that currency and {@code rate}
 * the rate in force on the opening day that valued it, copied (ق-ب٤); both are {@code null} for a
 * treasury in the base, and the rate is {@code null} too for an opening of zero, which needs none - a
 * dollar drawer opened empty must not be refused for want of a rate nobody needs.
 */
public record TreasuryOpening(Integer currencyId, BigDecimal amount, BigDecimal foreign, BigDecimal rate) {

    /** A treasury in the base: the opening is what was typed, and nothing foreign is stored. */
    public static TreasuryOpening base(BigDecimal amount) {
        return new TreasuryOpening(null, amount == null ? BigDecimal.ZERO : amount, null, null);
    }

    /**
     * A treasury in {@code currency}, opened with {@code foreign} of it.
     *
     * @param rate the rate in force on the opening day, or {@code null} when none is recorded - a refusal
     *             unless the opening is zero
     */
    public static TreasuryOpening foreign(Currency currency, BigDecimal foreign, BigDecimal rate)
            throws UserValidationException {
        requireUsable(currency);
        BigDecimal opening = foreign == null ? BigDecimal.ZERO : foreign;
        TreasuryExchange.requirePlaces(opening.abs(), currency);
        if (opening.signum() == 0) {
            return new TreasuryOpening(currency.id(), BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        if (rate == null || rate.signum() <= 0) {
            throw new UserValidationException("currency.error.no.rate");
        }
        return new TreasuryOpening(currency.id(), TreasuryExchange.baseOf(opening, rate), opening, rate);
    }

    /**
     * A currency a treasury may be put in: one that exists and is offered. The base is not refused -
     * the caller stores it as {@code null}, since a treasury never names the base (ق-ب١).
     */
    public static void requireUsable(Currency currency) throws UserValidationException {
        if (currency == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        if (!currency.active()) {
            throw new UserValidationException("treasury.currency.error.inactive");
        }
    }
}
