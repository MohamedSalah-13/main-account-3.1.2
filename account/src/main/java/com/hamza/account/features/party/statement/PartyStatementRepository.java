package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** The read side of a party statement. One implementation over JDBC; mocked in tests. */
public interface PartyStatementRepository {

    List<PartyStatementRow> search(PartyStatementFilter filter) throws DaoException;

    PartyStatementSummary summarize(PartyStatementFilter filter) throws DaoException;

    PartyStatementOptions options() throws DaoException;

    /**
     * The date of the party's first movement, which is where a statement opens.
     * <p>
     * Asked of the database rather than worked out from a loaded list: that is what
     * {@code AccountDetailsController.miniDate} did, and it cost a full read of the
     * ledger to learn one date.
     *
     * @return the earliest date, or null when the party has never moved
     */
    LocalDate earliestMovement(PartyKind kind, int partyId) throws DaoException;

    /** What the party owes today: their whole history summed. Never null; zero for a new party. */
    java.math.BigDecimal currentBalance(PartyKind kind, int partyId) throws DaoException;
}
