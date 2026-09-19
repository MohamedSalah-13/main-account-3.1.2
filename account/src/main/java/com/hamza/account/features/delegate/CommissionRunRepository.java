package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** The commission runs, their lines and their postings. An interface so the rules are testable without MySQL. */
public interface CommissionRunRepository {

    /** The id of the approved run of a month, if it has one. A cancelled run is not this answer. */
    Optional<Integer> activeRunId(YearMonth period) throws DaoException;

    /** Writes the run and answers its id. Joins the caller's transaction. */
    int insertRun(YearMonth period, String notes, int userId) throws DaoException;

    int insertLine(int runId, CommissionLine line) throws DaoException;

    List<CommissionRun> runs() throws DaoException;

    Optional<CommissionRun> run(int runId) throws DaoException;

    List<CommissionLine> linesOf(int runId) throws DaoException;

    /** 1 when the run moved from APPROVED to CANCELLED, 0 when it was no longer APPROVED. */
    int cancel(int runId, int userId, String reason) throws DaoException;

    /** A line still to be posted: its id, whose it is, and how much. */
    record Unposted(int lineId, int employeeId, BigDecimal amount) {
    }

    /** The run's postable lines, locked for the transaction. */
    List<Unposted> unpostedLinesForUpdate(int runId) throws DaoException;

    /** Writes the COMMISSION ledger row and answers its id. */
    int insertLedgerCommission(int employeeId, LocalDate date, BigDecimal amount, String notes, int userId)
            throws DaoException;

    int insertAccountPosting(int lineId, int ledgerEntryId, int userId) throws DaoException;

    /** What a payroll of {@code period} owes this delegate: approved, unposted, of that period or earlier. */
    BigDecimal payrollDue(int employeeId, YearMonth period) throws DaoException;

    /** Marks exactly the lines {@link #payrollDue} summed as paid by that payroll run. */
    int insertPayrollPostings(int payrollRunId, int userId, int employeeId, YearMonth period) throws DaoException;

    boolean ruleOfDayUsed(int employeeId, LocalDate effectiveFrom) throws DaoException;

    boolean ruleUsed(int ruleId) throws DaoException;
}
