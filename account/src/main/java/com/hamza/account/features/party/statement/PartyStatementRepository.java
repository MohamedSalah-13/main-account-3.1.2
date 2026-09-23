package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** The read side of a party statement. One implementation over JDBC; mocked in tests. */
public interface PartyStatementRepository {

    List<PartyStatementRow> search(PartyStatementFilter filter) throws DaoException;

    /** The period's figures, in the base and in the party's own currency (V82). */
    PartyStatementTotals summarize(PartyStatementFilter filter) throws DaoException;

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

    /** What the party owes today in its own currency (V82); the base figure for a party in the base. */
    java.math.BigDecimal currentBalanceOwn(PartyKind kind, int partyId) throws DaoException;

    /** The currency the party deals in, or {@code null} for the base - read with the rows it describes. */
    com.hamza.account.features.currency.Currency currencyOf(PartyKind kind, int partyId) throws DaoException;

    /**
     * The party's running balance on one movement's row of the statement, in the base and in its own
     * currency.
     *
     * @return the balance straight after that movement, or null when it is not in the ledger
     */
    MovementBalance balanceAfterMovement(PartyKind kind, int partyId, PartyMovementKind movement,
                                         long number) throws DaoException;
}
