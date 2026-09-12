package com.hamza.account.features.employee.statement;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything one employee's statement reads and everything a hand-entered movement writes.
 * <p>
 * An implementation is free to be a mock, which is what makes {@link EmployeeStatementService}
 * testable without a database - and the guards are the part worth testing that way.
 */
public interface EmployeeStatementRepository {

    List<EmployeeStatementRow> page(EmployeeStatementFilter filter) throws DaoException;

    EmployeeStatementSummary summarize(EmployeeStatementFilter filter) throws DaoException;

    /** The first day anything happened, or {@code null} for an employee with no movement. */
    LocalDate earliestMovement(int employeeId) throws DaoException;

    /** Read from {@code employee_balance}, never summed a second time here. */
    BigDecimal currentBalance(int employeeId) throws DaoException;

    List<EmployeeStatementUserOption> usersWhoEntered(int employeeId) throws DaoException;

    int insertLedgerEntry(int employeeId, LocalDate date, String kind, BigDecimal amount,
                          String notes, int userId) throws DaoException;

    /** The payroll run that wrote a ledger row, or {@code null} when a person did. */
    Integer ledgerRunOf(int employeeId, int entryId) throws DaoException;

    int deleteLedgerEntry(int employeeId, int entryId) throws DaoException;

    /** Files a payment under what it was for. Written in the payment's own transaction. */
    int insertPurpose(int expenseId, String purpose, Integer payrollRunId, int userId)
            throws DaoException;
}
