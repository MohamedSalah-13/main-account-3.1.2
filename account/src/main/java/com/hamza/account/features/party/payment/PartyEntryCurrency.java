package com.hamza.account.features.party.payment;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConverter;

import java.math.BigDecimal;

/**
 * What the collection screen needs to know about currencies before anything is saved (V82,
 * docs/currency-plan.md §14 ق-ج٤ and ق-ج٥): which currency the amount box is typed in, and what the
 * typed amount does to the party's balance in the party's own currency. The save asks the same
 * questions of {@code PartyMovementFigures}; this answers them for the screen, without a database.
 *
 * @param party    the party's currency, or {@code null} for the base
 * @param treasury the chosen treasury's currency, or {@code null} for the base
 * @param rate     the party's currency's rate on the movement's day, or {@code null} when none is
 *                 recorded - unused for a party in the base
 */
public record PartyEntryCurrency(Currency party, Currency treasury, BigDecimal rate) {

    /** A party in the base: every figure is the base. */
    public static final PartyEntryCurrency BASE = new PartyEntryCurrency(null, null, null);

    public PartyEntryCurrency {
        party = party == null || party.base() ? null : party;
        treasury = treasury == null || treasury.base() ? null : treasury;
    }

    /** Whether the party deals in a currency other than the base. */
    public boolean isForeign() {
        return party != null;
    }

    /**
     * The currency the amount is typed in, or {@code null} for the base: a note is in the party's
     * currency, and cash in the currency of the treasury it goes through.
     */
    public Currency typedIn(PartyEntryKind kind) {
        if (!isForeign()) {
            return null;
        }
        return kind.movesCash() ? treasury : party;
    }

    /**
     * What {@code amount}, typed in {@link #typedIn}, does to what the party owes in its own currency -
     * negative for a collection and a credit note. {@code null} when it cannot be said: pounds from a
     * dollar customer with no rate recorded for the day.
     */
    public BigDecimal ownChange(PartyEntryKind kind, BigDecimal amount) {
        BigDecimal typed = amount == null ? BigDecimal.ZERO : amount;
        if (!kind.movesCash()) {
            return typed.multiply(BigDecimal.valueOf(kind.sign()));
        }
        if (!isForeign() || treasury != null) {
            return typed.negate();
        }
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        return CurrencyConverter.fromBase(typed, rate, party).negate();
    }
}
