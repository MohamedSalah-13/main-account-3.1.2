package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A movement somebody records by hand on an employee's account — and never a movement of cash.
 * <p>
 * <b>A cash amount here is a defect, not a feature.</b> Every pound that leaves a till for an
 * employee is an {@code expenses_details} row written by {@code EmployeePaymentService}; this
 * records what is <em>not</em> cash: what was earned, awarded, deducted, or carried in. The
 * statement unions the two. ق-١ of {@code docs/employees-plan.md}, and the reason
 * {@code employee_ledger} has no treasury column to write one into.
 * <p>
 * The amount is unsigned and the direction is {@link EmployeeEntryKind#sign()}'s, which the
 * database enforces with its own CHECK. Validation returns message keys, never sentences.
 */
public record EmployeeLedgerEntry(int employeeId,
                                  LocalDate date,
                                  EmployeeEntryKind kind,
                                  BigDecimal amount,
                                  String notes) {

    private static final int NOTES_MAX = 255;

    /** {@code DECIMAL(14, 2)}: twelve digits before the point. */
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999999.99");

    public static EmployeeLedgerEntry parse(int employeeId, LocalDate date, EmployeeEntryKind kind,
                                            BigDecimal amount, String notes)
            throws UserValidationException {
        if (employeeId <= 0) {
            throw new UserValidationException("employee.error.account.employee");
        }
        if (date == null) {
            throw new UserValidationException("employee.error.account.date");
        }
        if (kind == null) {
            throw new UserValidationException("employee.error.account.kind");
        }
        if (!kind.mayBeEnteredByHand()) {
            // A commission is approved by a run, by definition. Typing one would be a figure
            // nothing approved, sitting in the balance the employee signs for.
            throw new UserValidationException("employee.error.account.kind.automatic");
        }
        if (amount == null || amount.signum() <= 0) {
            // Zero as well as negative: a movement of nothing is not a correction of anything,
            // and a minus sign here would be a second way of saying what the kind already says.
            throw new UserValidationException("employee.error.account.amount");
        }
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw new UserValidationException("employee.error.account.amount.range");
        }
        String cleanNotes = notes == null ? "" : notes.strip();
        if (cleanNotes.codePointCount(0, cleanNotes.length()) > NOTES_MAX) {
            throw new UserValidationException("employee.error.account.notes.length");
        }
        return new EmployeeLedgerEntry(employeeId, date, kind,
                amount.setScale(2, RoundingMode.HALF_UP),
                cleanNotes.isEmpty() ? null : cleanNotes);
    }
}
