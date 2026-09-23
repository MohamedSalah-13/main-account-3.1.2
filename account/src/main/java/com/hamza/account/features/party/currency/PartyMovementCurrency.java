package com.hamza.account.features.party.currency;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The currency side of saving a hand-entered movement on a party's account (V82,
 * docs/currency-plan.md §14 ق-ج٤ and ق-ج٥): reads the party's currency, the treasury's and the day's
 * rate, decides what the movement stores through {@link PartyMovementFigures}, and writes the foreign
 * half beside the row. {@code AccountCustomerService} and {@code AccountSupplierService} ask it before
 * anything else reads the movement's amounts, and put the base figures it answers back on the movement -
 * so the shift journal, the wallet fee and the treasury all go on seeing the base, as they always did.
 */
public final class PartyMovementCurrency {

    private final PartyCurrencies currencies;

    public PartyMovementCurrency(PartyCurrencies currencies) {
        this.currencies = currencies;
    }

    public static PartyMovementCurrency jdbc() {
        return new PartyMovementCurrency(PartyCurrencies.jdbc());
    }

    /**
     * What the movement stores.
     *
     * @param paid      the cash as typed - in the treasury's currency when that is the party's, else in
     *                  the base
     * @param purchase  a note's signed amount as typed, in the party's currency
     * @param walletFee the e-wallet's fee on this movement, or zero - refused on a treasury in a foreign
     *                  currency, where a fee would be an expense in dollars (ق-ب٦)
     */
    public PartyMovementFigures figures(PartyKind kind, int partyId, Integer treasuryId, String treasuryName,
                                        BigDecimal paid, BigDecimal purchase, LocalDate day, BigDecimal walletFee)
            throws DaoException {
        Currency party = currencies.ofParty(kind, partyId);
        boolean movesCash = paid != null && paid.signum() != 0;
        Currency treasury = movesCash && treasuryId != null ? currencies.ofTreasury(treasuryId) : null;
        if (treasury != null && !treasury.base() && walletFee != null && walletFee.signum() > 0) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString(
                    "party.currency.error.fee", treasuryName == null ? "" : treasuryName));
        }
        BigDecimal rate = party == null || party.base() ? null : currencies.rateOn(party.id(), day);
        return PartyMovementFigures.of(party, treasury, treasuryName, paid, purchase, rate);
    }

    /**
     * Writes the foreign half of a movement just stored. A new movement in the base has nothing to
     * write; an edited one is written either way, so a figure left over from before is cleared.
     */
    public void write(PartyKind kind, long movementId, PartyMovementFigures figures, boolean updating)
            throws DaoException {
        if (figures.isForeign() || updating) {
            currencies.writeMovement(kind, movementId, figures);
        }
    }

    /** The currency a party deals in, for a screen labelling its amounts; {@code null} for the base. */
    public Currency ofParty(PartyKind kind, int partyId) throws DaoException {
        return currencies.ofParty(kind, partyId);
    }

    /** The currency a treasury is in; {@code null} for the base. */
    public Currency ofTreasury(int treasuryId) throws DaoException {
        return currencies.ofTreasury(treasuryId);
    }

    /** The rate in force on a day, or {@code null}. */
    public BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException {
        return currencies.rateOn(currencyId, day);
    }
}
