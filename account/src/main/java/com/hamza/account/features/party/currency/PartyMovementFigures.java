package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConverter;
import com.hamza.account.features.treasury.TreasuryExchange;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a hand-entered movement on a party's account stores (V82, docs/currency-plan.md §14 ق-ج٤ and
 * ق-ج٥): its two base columns, and for a party in a foreign currency the same two in that currency with
 * the day's rate.
 * <p>
 * <b>Which currency a figure is typed in is decided here, once, for the screen and the save alike</b>
 * ({@link #cashCurrency}). A note moves no cash and is typed in the party's currency. Cash is typed in
 * the currency of the treasury it moves through - which may be the party's own or the base, and nothing
 * else: a dollar customer's collection goes into a dollar drawer as dollars, or into the pound till as
 * the pounds that reached it, and a riyal account takes neither. A party in the base deals in the base,
 * so a foreign treasury is refused for it as the third currency it is.
 * <p>
 * Each figure is converted once: dollars into a dollar drawer are valued in the base at the rate, and
 * pounds into the till are worth that many dollars at the rate. The rate is the one in force on the
 * movement's day, copied onto the row (ق-٤), and there being none is a refusal (ق-٣).
 *
 * @param paid            the {@code paid} column, in the base - what the treasury moved, valued
 * @param purchase        the {@code purchase} column, in the base - a note's signed amount, valued
 * @param paidForeign     {@code paid} in the party's currency, or {@code null} for a party in the base
 * @param purchaseForeign {@code purchase} in it, or {@code null}
 * @param rate            the rate both were converted at, or {@code null}
 */
public record PartyMovementFigures(BigDecimal paid, BigDecimal purchase,
                                   BigDecimal paidForeign, BigDecimal purchaseForeign, BigDecimal rate) {

    public PartyMovementFigures {
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(purchase, "purchase");
        if ((rate == null) != (paidForeign == null) || (rate == null) != (purchaseForeign == null)) {
            throw new IllegalArgumentException("The foreign figures and their rate go together");
        }
    }

    /** A party in the base: the two columns are what was typed, and nothing foreign is stored. */
    public static PartyMovementFigures base(BigDecimal paid, BigDecimal purchase) {
        return new PartyMovementFigures(orZero(paid), orZero(purchase), null, null, null);
    }

    /**
     * The currency cash is typed in, for a party in {@code party} moving it through a treasury in
     * {@code treasury} - {@code null} for the base.
     *
     * @throws BusinessRuleException when the treasury is in neither the party's currency nor the base
     */
    public static Currency cashCurrency(Currency party, Currency treasury, String treasuryName)
            throws BusinessRuleException {
        if (isBase(treasury)) {
            return null;
        }
        if (!isBase(party) && party.id() == treasury.id()) {
            return party;
        }
        throw new BusinessRuleException(LanguageManager.getInstance().getString(
                "party.currency.error.treasury", treasuryName == null ? "" : treasuryName));
    }

    /**
     * What a movement stores.
     *
     * @param party        the party's currency, {@code null} for the base
     * @param treasury     the currency of the treasury the cash moves through, {@code null} for the base;
     *                     read only when there is cash
     * @param treasuryName for the refusal's sentence
     * @param paid         the cash as typed, in {@link #cashCurrency}
     * @param purchase     a note's signed amount as typed, in the party's currency
     * @param rate         the party's currency's rate on the movement's day, or {@code null} when none is
     *                     recorded - a refusal for a party in a foreign currency, unused for one in the base
     */
    public static PartyMovementFigures of(Currency party, Currency treasury, String treasuryName,
                                          BigDecimal paid, BigDecimal purchase, BigDecimal rate)
            throws UserValidationException, BusinessRuleException {
        BigDecimal cash = orZero(paid);
        BigDecimal note = orZero(purchase);
        Currency typedIn = cash.signum() == 0 ? null : cashCurrency(party, treasury, treasuryName);
        if (isBase(party)) {
            return base(cash, note);
        }
        if (rate == null || rate.signum() <= 0) {
            throw new UserValidationException("currency.error.no.rate");
        }
        TreasuryExchange.requirePlaces(note.abs(), party);
        BigDecimal paidForeign;
        BigDecimal paidBase;
        if (typedIn == null) {
            TreasuryExchange.requirePlaces(cash.abs(), null);
            paidBase = cash;
            paidForeign = cash.signum() == 0 ? BigDecimal.ZERO : CurrencyConverter.fromBase(cash, rate, party);
        } else {
            TreasuryExchange.requirePlaces(cash.abs(), party);
            paidForeign = cash;
            paidBase = TreasuryExchange.baseOf(cash, rate);
        }
        return new PartyMovementFigures(paidBase, TreasuryExchange.baseOf(note, rate), paidForeign, note, rate);
    }

    /** Whether the movement carries figures in a foreign currency. */
    public boolean isForeign() {
        return rate != null;
    }

    /** The cash in the party's own currency - what an allocation to one of its invoices is measured in. */
    public BigDecimal paidOwn() {
        return isForeign() ? paidForeign : paid;
    }

    private static boolean isBase(Currency currency) {
        return currency == null || currency.base();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
