package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.treasury.TreasuryCurrencies;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Settles a party's currency and values its opening balance before the row is saved (V82,
 * docs/currency-plan.md §14 ق-ج١ and ق-ج٢).
 * <p>
 * The form hands over the currency it chose and, for a party in a foreign currency, the opening typed
 * in that currency ({@code opening_foreign}); a party in the base keeps typing its opening straight into
 * {@code first_balance}. What leaves here is the row as it is stored: a foreign opening valued into
 * {@code first_balance} at the rate in force on the opening's day, with the rate copied - or, for the
 * base, no currency and nothing foreign at all.
 * <p>
 * <b>The currency is fixed by the first movement</b>, with the opening itself: the same lock
 * ({@link OpeningBalanceRegistry}) that stops {@code first_balance} being rewritten once anything has
 * moved the party. A changed currency after that is refused here, with a sentence of its own, because the
 * update would otherwise leave it out without a word. <b>An opening that did not change keeps its stored
 * value and rate</b>, as a treasury's does: recording a rate for its day later does not revalue it.
 */
public final class PartyOpeningCurrency {

    /** The database's side, a seam for the tests. */
    public interface Lookup {
        Currency find(Integer currencyId) throws DaoException;

        BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException;

        boolean hasMoved(PartyKind kind, int partyId) throws DaoException;
    }

    private final Lookup lookup;

    public PartyOpeningCurrency(Lookup lookup) {
        this.lookup = lookup;
    }

    public static PartyOpeningCurrency jdbc() {
        TreasuryCurrencies currencies = TreasuryCurrencies.jdbc();
        return new PartyOpeningCurrency(new Lookup() {
            @Override
            public Currency find(Integer currencyId) throws DaoException {
                return currencies.find(currencyId);
            }

            @Override
            public BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException {
                return currencies.rateOn(currencyId, day);
            }

            @Override
            public boolean hasMoved(PartyKind kind, int partyId) throws DaoException {
                return OpeningBalanceGuard.shared().isLocked(kind == PartyKind.CUSTOMER
                        ? OpeningBalanceRegistry.CUSTOMERS : OpeningBalanceRegistry.SUPPLIERS, partyId);
            }
        });
    }

    /**
     * Puts the stored currency columns on {@code party}.
     *
     * @param stored the party as it is stored now, or {@code null} for a new one
     * @param today  the day an opening with no date of its own is valued on
     */
    public void prepare(PartyKind kind, BaseNames party, BaseNames stored, LocalDate today) throws DaoException {
        Currency currency = party.getCurrency_id() == null ? null : lookup.find(party.getCurrency_id());
        if (currency != null && currency.base()) {
            currency = null;
        }
        Integer wanted = currency == null ? null : currency.id();

        if (stored != null && lookup.hasMoved(kind, stored.getId())) {
            if (!Objects.equals(wanted, stored.getCurrency_id())) {
                throw new BusinessRuleException(LanguageManager.getInstance().getString("party.currency.error.fixed"));
            }
            if (wanted == null) {
                clearForeign(party);
                return;
            }
            if (sameAmount(party.getOpening_foreign(), stored.getOpening_foreign())) {
                keepStored(party, stored);
                return;
            }
            // A changed foreign opening is valued below and then refused by the opening-balance guard,
            // with the sentence that names the movements - the one a changed base opening meets.
        }

        if (currency == null) {
            clearForeign(party);
            return;
        }
        if (stored != null && Objects.equals(stored.getCurrency_id(), wanted)
                && sameAmount(party.getOpening_foreign(), stored.getOpening_foreign())
                && Objects.equals(party.getOpening_balance_date(), stored.getOpening_balance_date())) {
            keepStored(party, stored);
            return;
        }
        BigDecimal foreign = party.getOpening_foreign() == null ? BigDecimal.ZERO : party.getOpening_foreign();
        LocalDate day = party.getOpening_balance_date() != null ? party.getOpening_balance_date() : today;
        BigDecimal rate = foreign.signum() == 0 ? null : lookup.rateOn(currency.id(), day);
        PartyOpening opening = PartyOpening.foreign(currency, foreign, rate);
        party.setCurrency_id(opening.currencyId());
        party.setOpening_foreign(opening.foreign());
        party.setOpening_rate(opening.rate());
        party.setFirst_balance(opening.amount().doubleValue());
    }

    private static void clearForeign(BaseNames party) {
        party.setCurrency_id(null);
        party.setOpening_foreign(null);
        party.setOpening_rate(null);
    }

    private static void keepStored(BaseNames party, BaseNames stored) {
        party.setCurrency_id(stored.getCurrency_id());
        party.setOpening_foreign(stored.getOpening_foreign());
        party.setOpening_rate(stored.getOpening_rate());
        party.setFirst_balance(stored.getFirst_balance());
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.compareTo(right) == 0;
    }
}
