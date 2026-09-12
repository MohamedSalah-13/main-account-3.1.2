package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Cash leaving a till for an employee: a salary, an advance, a bonus handed over, a settlement.
 *
 * @param expenseTypeCode the heading in {@code expenses} the row is filed under.
 *                        <b>Chosen by the person paying, never mapped from the purpose in code.</b>
 *                        A fixed {@code SALARY -> 1} would be the {@code UsersType} and
 *                        {@code DELEGATE_JOB} mistake again: the headings are an editable table, so
 *                        a shop may rename or delete the one a constant names. The purpose below is
 *                        the single definition the employee's statement reads; the heading is the
 *                        expenses screen's own classification, and an expense row has carried one
 *                        since V1
 * @param purpose         what the payment was for - {@code SALARY} when nothing says otherwise,
 *                        which is what the years of existing rows already mean
 */
public record EmployeePayment(int employeeId,
                              LocalDate date,
                              BigDecimal amount,
                              EmployeeCashPurpose purpose,
                              int treasuryId,
                              int expenseTypeCode,
                              String notes) {

    private static final int NOTES_MAX = 255;

    /** {@code DECIMAL(14, 2)}, and the expenses table's own CHECK refuses a negative. */
    private static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999999.99");

    public static EmployeePayment parse(int employeeId, LocalDate date, BigDecimal amount,
                                        EmployeeCashPurpose purpose, int treasuryId,
                                        int expenseTypeCode, String notes)
            throws UserValidationException {
        if (employeeId <= 0) {
            throw new UserValidationException("employee.error.account.employee");
        }
        if (date == null) {
            throw new UserValidationException("employee.error.account.date");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new UserValidationException("employee.error.pay.amount");
        }
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw new UserValidationException("employee.error.account.amount.range");
        }
        if (treasuryId <= 0) {
            throw new UserValidationException("employee.error.pay.treasury");
        }
        if (expenseTypeCode <= 0) {
            throw new UserValidationException("employee.error.pay.heading");
        }
        String cleanNotes = notes == null ? "" : notes.strip();
        if (cleanNotes.codePointCount(0, cleanNotes.length()) > NOTES_MAX) {
            throw new UserValidationException("employee.error.account.notes.length");
        }
        return new EmployeePayment(employeeId, date, amount.setScale(2, RoundingMode.HALF_UP),
                purpose == null ? EmployeeCashPurpose.SALARY : purpose, treasuryId,
                expenseTypeCode, cleanNotes.isEmpty() ? null : cleanNotes);
    }
}
