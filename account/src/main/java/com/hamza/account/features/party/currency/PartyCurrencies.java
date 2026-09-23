package com.hamza.account.features.party.currency;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.treasury.TreasuryCurrencies;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The database side of a party's currency (V82, docs/currency-plan.md §14): which currency a party and a
 * treasury are in, the rate on a day, and the foreign figures a movement and a document carry. A seam,
 * so the services that use it are tested without a database.
 * <p>
 * The reads are unguarded, as {@code TreasuryCurrencies}' are: they value a movement somebody is already
 * permitted to write.
 */
public interface PartyCurrencies {

    /** What a stored document says about its translation, or {@code null} for no such document. */
    record StoredDocument(int partyId, LocalDate date, BigDecimal rate) {
    }

    /** The three base figures of a document's header. */
    record DocumentAmounts(BigDecimal total, BigDecimal discount, BigDecimal paid) {
    }

    /** The currency a party deals in; {@code null} for the base. */
    Currency ofParty(PartyKind kind, int partyId) throws DaoException;

    /** The currency a treasury is in; {@code null} for the base. */
    Currency ofTreasury(int treasuryId) throws DaoException;

    /** The rate in force on {@code day} - the latest dated on it or before it - or {@code null}. */
    BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException;

    /** Writes a movement's foreign figures, or clears them for figures in the base. */
    void writeMovement(PartyKind kind, long movementId, PartyMovementFigures figures) throws DaoException;

    StoredDocument storedDocument(DocumentType type, long number) throws DaoException;

    DocumentAmounts documentAmounts(DocumentType type, long number) throws DaoException;

    /** Writes a document's translation, or clears it for {@code null}. */
    void writeDocument(DocumentType type, long number, DocumentTranslation translation) throws DaoException;

    static PartyCurrencies jdbc() {
        return new JdbcPartyCurrencies(TreasuryCurrencies.jdbc());
    }
}
