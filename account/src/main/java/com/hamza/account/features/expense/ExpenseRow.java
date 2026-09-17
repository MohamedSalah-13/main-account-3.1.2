package com.hamza.account.features.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One expense as the list shows it, with every name already read.
 * <p>
 * The names come off one statement's joins rather than a query per row: the old mapper was cheap only
 * because it read the view, and a list that resolves its heading, its treasury, its employee and its
 * user one row at a time is {@code ItemsDao.map} again.
 *
 * @param employeeId   the employee a salary or an advance was paid to, or {@code null} for every other
 *                     expense. Written by the employee payment screen, never by the expenses screen
 * @param shiftId      the shift the cash left under, or {@code null}
 * @param enteredAt    when the row was written, which is not {@link #date}: an expense is dated by the
 *                     day it was paid, and entered whenever somebody sat down to enter it
 */
public record ExpenseRow(int id,
                         LocalDate date,
                         int headingId,
                         String headingName,
                         String parentHeadingName,
                         boolean employeePaymentHeading,
                         int treasuryId,
                         String treasuryName,
                         BigDecimal amount,
                         String payee,
                         String referenceNo,
                         String notes,
                         Integer employeeId,
                         String employeeName,
                         int userId,
                         String userName,
                         Integer shiftId,
                         LocalDateTime enteredAt) {

    /** "الإدارية › الكهرباء", or the heading alone when it is a main one. */
    public String headingPath() {
        return parentHeadingName == null || parentHeadingName.isBlank()
                ? headingName : parentHeadingName + ExpenseHeading.PATH_SEPARATOR + headingName;
    }

    /** A salary or an advance, paid through the employee payment screen. */
    public boolean paidToEmployee() {
        return employeeId != null;
    }

    /** What the form edits, taken from the stored row - never from a screen that may have gone stale. */
    public ExpenseEntry toEntry() {
        return new ExpenseEntry(id, date, headingId, treasuryId, amount, payee, referenceNo, notes);
    }
}
