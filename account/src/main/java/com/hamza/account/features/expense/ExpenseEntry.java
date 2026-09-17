package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * An expense as a person enters it: what it was for, which till paid it, how much, and to whom.
 * <p>
 * No employee here. Paying an employee is the employee payment screen's, which says what the payment
 * was for; the expenses screen used to take a salary or an advance without that, and an advance then
 * read as a salary on the employee's account (docs/expenses-plan.md ع-٣, ق-٥). {@link ExpenseService}
 * takes the employee as an argument of its own for the one caller entitled to it.
 * <p>
 * The limits are the schema's, read off it: {@code DECIMAL(14, 2)} and a {@code CHECK (amount >= 0)},
 * {@code VARCHAR(255)} notes since V1, and V64's {@code VARCHAR(100)} payee and {@code VARCHAR(50)}
 * reference.
 *
 * @param id 0 for a new expense
 */
public record ExpenseEntry(int id,
                           LocalDate date,
                           int headingId,
                           int treasuryId,
                           BigDecimal amount,
                           String payee,
                           String referenceNo,
                           String notes) {

    public static final BigDecimal AMOUNT_MAX = new BigDecimal("999999999999.99");
    public static final int PAYEE_MAX = 100;
    public static final int REFERENCE_MAX = 50;
    public static final int NOTES_MAX = 255;

    /**
     * Reads what a form holds into an entry, or refuses it with the key of the first thing wrong.
     * <p>
     * The amount is rounded HALF_UP to the two places the column stores, here, once - so the figure the
     * screen confirmed and the figure written are one number.
     */
    public static ExpenseEntry parse(int id, LocalDate date, int headingId, int treasuryId,
                                     BigDecimal amount, String payee, String referenceNo, String notes)
            throws UserValidationException {
        if (date == null) {
            throw new UserValidationException("expense.error.date");
        }
        if (headingId <= 0) {
            throw new UserValidationException("expense.error.heading");
        }
        if (treasuryId <= 0) {
            throw new UserValidationException("expenses.error.select.treasury");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new UserValidationException("expense.error.amount");
        }
        if (amount.compareTo(AMOUNT_MAX) > 0) {
            throw new UserValidationException("expense.error.amount.range");
        }
        String cleanPayee = clean(payee);
        if (length(cleanPayee) > PAYEE_MAX) {
            throw new UserValidationException("expense.error.payee.length");
        }
        String cleanReference = clean(referenceNo);
        if (length(cleanReference) > REFERENCE_MAX) {
            throw new UserValidationException("expense.error.reference.length");
        }
        String cleanNotes = clean(notes);
        if (length(cleanNotes) > NOTES_MAX) {
            throw new UserValidationException("expense.error.notes.length");
        }
        return new ExpenseEntry(Math.max(id, 0), date, headingId, treasuryId,
                amount.setScale(2, RoundingMode.HALF_UP), cleanPayee, cleanReference, cleanNotes);
    }

    public boolean isNew() {
        return id <= 0;
    }

    /** The same expense under another id - what a saved entry becomes once the database numbers it. */
    public ExpenseEntry withId(int newId) {
        return new ExpenseEntry(newId, date, headingId, treasuryId, amount, payee, referenceNo, notes);
    }

    /** Blank is absent: an empty payee stored as {@code ''} and one stored as NULL would search apart. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }
}
