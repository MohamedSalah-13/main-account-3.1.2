package com.hamza.account.features.expense.recurring;

import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the recurring-expense form holds, and every rule about it - kept together because each rule is a
 * sentence about the form rather than about the database, and each is a message key.
 *
 * @param id      the template being corrected, or 0 for a new one
 * @param endDate {@code null} for an open end
 */
public record ExpenseRecurringDraft(int id, int headingId, int treasuryId, BigDecimal amount, String payee,
                                    String notes, ExpenseFrequency frequency, int dayOfMonth,
                                    LocalDate startDate, LocalDate endDate, boolean active) {

    /** The most a template may carry, as {@code DECIMAL(15,2)} allows. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    public ExpenseRecurringDraft {
        payee = payee == null ? "" : payee.strip();
        notes = notes == null ? "" : notes.strip();
    }

    public boolean isNew() {
        return id <= 0;
    }

    /**
     * Refuses a template that cannot be saved.
     * <p>
     * <b>An employee heading is refused</b>, for the reason ق-٥ gives: an employee is paid through the
     * employee payment screen, which records what the payment was for beside it. A template that
     * reminded somebody to record a salary as an ordinary expense would walk around that.
     */
    public void require(ExpenseHeading heading) throws UserValidationException {
        if (heading == null) {
            throw new UserValidationException("expense.recurring.error.heading");
        }
        if (!heading.active()) {
            throw new UserValidationException("expense.recurring.error.heading.stopped");
        }
        if (heading.employeePayment()) {
            throw new UserValidationException("expense.recurring.error.heading.employee");
        }
        if (treasuryId <= 0) {
            throw new UserValidationException("expense.recurring.error.treasury");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new UserValidationException("expense.recurring.error.amount");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new UserValidationException("expense.recurring.error.amount.big");
        }
        if (frequency == null) {
            throw new UserValidationException("expense.recurring.error.frequency");
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new UserValidationException("expense.recurring.error.day");
        }
        if (startDate == null) {
            throw new UserValidationException("expense.recurring.error.start");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new UserValidationException("expense.recurring.error.period");
        }
    }
}
