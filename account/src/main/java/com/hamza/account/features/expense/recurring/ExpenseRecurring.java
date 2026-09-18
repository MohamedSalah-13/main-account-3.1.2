package com.hamza.account.features.expense.recurring;

import com.hamza.account.features.expense.ExpenseHeading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A recurring expense: a template for something the shop pays every month, quarter or year - rent, a
 * subscription, a licence.
 * <p>
 * <b>It records nothing by itself</b> (docs/expenses-plan.md §5.2). The cash leaves a drawer by a
 * person's hand, and {@code ShiftGate} asks for an open shift belonging to that person on that till -
 * and there is nobody inside a scheduled task. The template reminds; the entry screen records.
 *
 * @param endDate the day after which it stops falling due, or {@code null} for an open end
 */
public record ExpenseRecurring(int id, int headingId, String headingName, String parentHeadingName,
                               int treasuryId, String treasuryName, BigDecimal amount, String payee,
                               String notes, ExpenseFrequency frequency, int dayOfMonth,
                               LocalDate startDate, LocalDate endDate, boolean active) {

    public ExpenseRecurring {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(startDate, "startDate");
        amount = amount == null ? BigDecimal.ZERO : amount;
        payee = payee == null ? "" : payee;
        notes = notes == null ? "" : notes;
    }

    /** "الإدارية › الإيجار", or the heading alone when it is a main one. */
    public String headingPath() {
        return parentHeadingName == null || parentHeadingName.isBlank()
                ? headingName : parentHeadingName + ExpenseHeading.PATH_SEPARATOR + headingName;
    }

    /** Whether the template covers a day at all - it has started, has not ended, and is not stopped. */
    public boolean coversDay(LocalDate day) {
        return active && !day.isBefore(startDate) && (endDate == null || !day.isAfter(endDate));
    }

    /** The first day of the period that {@code day} falls in, counted from this template's start. */
    public LocalDate periodStart(LocalDate day) {
        return frequency.periodStart(startDate, day);
    }

    /** The day this template is due inside the period starting on {@code periodStart}. */
    public LocalDate dueDate(LocalDate periodStart) {
        return frequency.dueDate(periodStart, dayOfMonth);
    }
}
