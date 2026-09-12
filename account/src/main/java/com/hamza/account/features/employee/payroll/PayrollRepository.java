package com.hamza.account.features.employee.payroll;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What the payroll needs out of a database, named so the service can be tested without one.
 * <p>
 * Every method here is a read or a single write. The ordering between them - what must happen
 * inside one transaction, and in which order - belongs to {@link PayrollService}, which is
 * where the operation is: the boundary belongs to the operation, not to a table
 * ({@code CLAUDE.md}, "Database access").
 */
public interface PayrollRepository {

    List<PayrollRun> recentRuns(int limit) throws DaoException;

    Optional<PayrollRun> findRun(int runId) throws DaoException;

    Optional<PayrollRun> findRunForPeriod(PayrollPeriod period) throws DaoException;

    int insertRun(PayrollPeriod period, String notes, int userId) throws DaoException;

    /**
     * Moves a run's status, and only from {@code expected}.
     *
     * @return 1 when this caller made the move, 0 when somebody else got there first
     */
    int moveStatus(int runId, PayrollRunStatus expected, PayrollRunStatus next, int userId)
            throws DaoException;

    int deleteDraftRun(int runId) throws DaoException;

    List<PayrollLine> linesOf(int runId) throws DaoException;

    /** Everyone employed on any day of the period, with the rate effective in it. */
    List<PayrollInput> candidatesFor(PayrollPeriod period) throws DaoException;

    int deleteLines(int runId) throws DaoException;

    /** Rewrites one line of a draft, its inputs and its recalculated figures together. */
    int updateLine(int runId, int lineId, PayrollCalculation calculation, PayrollInput input,
                   String notes) throws DaoException;

    int insertLine(int runId, PayrollCalculation calculation, PayrollInput input, int userId)
            throws DaoException;

    /** Writes one ledger row for a run, carrying the run's id so it can be taken back. */
    int insertRunLedgerEntry(int employeeId, LocalDate date, String kind, BigDecimal amount,
                             String notes, int runId, int userId) throws DaoException;

    int deleteRunLedgerEntries(int runId) throws DaoException;

    /** How many payments a run has already produced - counted, never inferred from a status. */
    int countRunPayments(int runId) throws DaoException;
}
