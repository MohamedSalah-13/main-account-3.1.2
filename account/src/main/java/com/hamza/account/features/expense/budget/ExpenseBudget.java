package com.hamza.account.features.expense.budget;

import com.hamza.account.features.expense.ExpenseHeading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A budget: what one heading is allowed in one year, or in one month of it.
 *
 * @param month      {@code null} for a budget covering the whole year
 * @param headingName the heading's own name, read with the row
 * @param parentName  its main heading's name, or {@code null} when it is one
 */
public record ExpenseBudget(int id, int headingId, String headingName, String parentName, int year,
                            Integer month, BigDecimal amount, String notes) {

    public ExpenseBudget {
        Objects.requireNonNull(amount, "amount");
        notes = notes == null ? "" : notes;
    }

    /** A budget for the year as a whole, rather than for one of its months. */
    public boolean isYearly() {
        return month == null;
    }

    /** "الإدارية › الكهرباء", or the heading alone when it is a main one. */
    public String headingPath() {
        return parentName == null || parentName.isBlank()
                ? headingName : parentName + ExpenseHeading.PATH_SEPARATOR + headingName;
    }

    /** The first day this budget covers. */
    public LocalDate from() {
        return LocalDate.of(year, month == null ? 1 : month, 1);
    }

    /** The last day it covers. */
    public LocalDate to() {
        return isYearly() ? LocalDate.of(year, 12, 31) : from().plusMonths(1).minusDays(1);
    }

    /** Whether this budget governs a given month of a given year. */
    public boolean covers(int otherYear, int otherMonth) {
        return year == otherYear && (isYearly() || month == otherMonth);
    }
}
