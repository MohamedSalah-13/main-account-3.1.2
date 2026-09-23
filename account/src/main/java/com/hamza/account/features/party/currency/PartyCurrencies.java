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

    /**
     * What a stored document says about its currency, or {@code null} for no such document.
     *
     * @param rate       the rate copied onto it, or {@code null} for a document of a party in the base
     * @param currencyId the currency it was <em>written</em> in (V83), or {@code null} for one written in
     *                   the base - which a document translated in V82 also is
     */
    record StoredDocument(int partyId, LocalDate date, BigDecimal rate, Integer currencyId) {

        /** A document of V82: written in the base and translated. */
        public StoredDocument(int partyId, LocalDate date, BigDecimal rate) {
            this(partyId, date, rate, null);
        }
    }

    /** The three base figures of a document's header. */
    record DocumentAmounts(BigDecimal total, BigDecimal discount, BigDecimal paid) {
    }

    /**
     * A document's header in its party's currency: the rate it is stored at, and the total, the discount
     * and the cash in that currency - as typed for a document written in it (V83, {@code currencyId} set),
     * as translated for one written in the base (V82, {@code currencyId} null).
     */
    record ForeignHeader(Integer currencyId, BigDecimal rate, BigDecimal total, BigDecimal discount,
                         BigDecimal paid) {

        /** Whether it was typed in the party's currency rather than translated into it. */
        public boolean written() {
            return currencyId != null;
        }
    }

    /** One line's price and discount as typed in its document's currency (V83). */
    record WrittenLine(BigDecimal price, BigDecimal discount) {
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

    /** A document's header in its party's currency, or {@code null} for a document of a party in the base. */
    ForeignHeader foreignHeader(DocumentType type, long number) throws DaoException;

    /** The lines of a document typed in its party's currency, by line id - empty for any other. */
    java.util.Map<Integer, WrittenLine> writtenLines(DocumentType type, long number) throws DaoException;

    /**
     * Writes a document's figures in its party's currency, or clears them for {@code null}.
     *
     * @param currencyId the currency the document was written in (V83), or {@code null} for one written in
     *                   the base and translated - the {@code translation} then is a translation
     */
    void writeDocument(DocumentType type, long number, Integer currencyId, DocumentTranslation translation)
            throws DaoException;

    static PartyCurrencies jdbc() {
        return new JdbcPartyCurrencies(TreasuryCurrencies.jdbc());
    }
}
