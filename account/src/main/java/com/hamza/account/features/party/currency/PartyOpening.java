package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.treasury.TreasuryExchange;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * What a party's opening balance stores, once its currency is known (V82, docs/currency-plan.md §14
 * ق-ج٢) - {@code TreasuryOpening} applied to a customer or a supplier.
 * <p>
 * {@code amount} is the opening in the base: {@code first_balance}, the column every balance has always
 * been measured from. For a party in a foreign currency, {@code foreign} is the opening in that currency
 * and {@code rate} the rate in force on the opening day that valued it, copied; both are {@code null} for
 * a party in the base, and the rate is {@code null} too for an opening of zero, which needs none - a
 * dollar customer entered with nothing owed must not be refused for want of a rate nobody needs.
 * <p>
 * An opening may be negative - a customer the shop owes, a supplier who owes the shop - and is valued
 * the same way.
 */
public record PartyOpening(Integer currencyId, BigDecimal amount, BigDecimal foreign, BigDecimal rate) {

    /** A party in the base: the opening is what was typed, and nothing foreign is stored. */
    public static PartyOpening base(BigDecimal amount) {
        return new PartyOpening(null, amount == null ? BigDecimal.ZERO : amount, null, null);
    }

    /**
     * A party in {@code currency}, opening with {@code foreign} of it.
     *
     * @param rate the rate in force on the opening day, or {@code null} when none is recorded - a refusal
     *             unless the opening is zero
     */
    public static PartyOpening foreign(Currency currency, BigDecimal foreign, BigDecimal rate)
            throws UserValidationException {
        requireUsable(currency);
        BigDecimal opening = foreign == null ? BigDecimal.ZERO : foreign;
        TreasuryExchange.requirePlaces(opening.abs(), currency);
        if (opening.signum() == 0) {
            return new PartyOpening(currency.id(), BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        if (rate == null || rate.signum() <= 0) {
            throw new UserValidationException("currency.error.no.rate");
        }
        return new PartyOpening(currency.id(), TreasuryExchange.baseOf(opening, rate), opening, rate);
    }

    /**
     * A currency a party may deal in: one that exists and is offered. The base is not refused - the
     * caller stores it as {@code null}, since a party never names the base (ق-ج١).
     */
    public static void requireUsable(Currency currency) throws UserValidationException {
        if (currency == null) {
            throw new UserValidationException("currency.error.not.found");
        }
        if (!currency.active()) {
            throw new UserValidationException("party.currency.error.inactive");
        }
    }

    /** Whether this is an opening in a foreign currency. */
    public boolean isForeign() {
        return currencyId != null;
    }
}
