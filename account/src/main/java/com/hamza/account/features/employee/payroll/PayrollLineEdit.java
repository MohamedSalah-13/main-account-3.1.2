package com.hamza.account.features.employee.payroll;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;

/**
 * The figures a person may type into a draft line - and only those.
 * <p>
 * The rate, the salary kind and the allowances are not here: they come from the employee's
 * record and from dated rows, and a screen that could overwrite them would be a second place
 * a salary is decided. The basic and the net are not here either, because they are computed -
 * {@link PayrollService#updateLine} recalculates them rather than storing what it was told.
 * <p>
 * {@code advancesOutstanding} is absent for the same reason it is absent from every
 * calculation: it is a fact about what already left the till, not an input (rule ق-٥).
 */
public record PayrollLineEdit(int lineId,
                              BigDecimal workedDays,
                              BigDecimal absenceDays,
                              BigDecimal workedHours,
                              BigDecimal commission,
                              BigDecimal deductions,
                              String notes) {

    private static final BigDecimal MAX = new BigDecimal("99999999.99");

    /** Refused with message keys, so the screen shows a sentence rather than a stack trace. */
    public static PayrollLineEdit parse(int lineId, BigDecimal workedDays, BigDecimal absenceDays,
                                        BigDecimal workedHours, BigDecimal commission,
                                        BigDecimal deductions, String notes)
            throws UserValidationException {
        if (lineId <= 0) {
            throw new UserValidationException("payroll.error.line.missing");
        }
        BigDecimal[] amounts = {workedDays, absenceDays, workedHours, commission, deductions};
        for (BigDecimal amount : amounts) {
            if (amount != null && (amount.signum() < 0 || amount.compareTo(MAX) > 0)) {
                throw new UserValidationException("payroll.error.line.amount");
            }
        }
        String cleanNotes = notes == null ? null : notes.trim();
        if (cleanNotes != null && cleanNotes.length() > 255) {
            throw new UserValidationException("payroll.error.line.notes");
        }
        return new PayrollLineEdit(lineId, workedDays, absenceDays, workedHours, commission,
                deductions, cleanNotes == null || cleanNotes.isEmpty() ? null : cleanNotes);
    }
}
