package com.hamza.account.features.employee.payroll;

import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The month a run is for, and the two dates every calculation measures against.
 * <p>
 * It exists because "September" is three different facts in a payroll: the first day, the
 * last day, and how many days there are between them - and the third is what a monthly
 * salary is divided by when somebody was absent. Writing {@code 30} anywhere would be wrong
 * eleven months of the year and wrong twice in a leap February.
 *
 * @param year  the calendar year
 * @param month 1-12
 */
public record PayrollPeriod(int year, int month) {

    public PayrollPeriod {
        if (year < 2000 || year > 2200) {
            throw new IllegalArgumentException("Year out of range: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month out of range: " + month);
        }
    }

    /** The period holding {@code date} - what "this month's payroll" means on any given day. */
    public static PayrollPeriod of(LocalDate date) {
        return new PayrollPeriod(date.getYear(), date.getMonthValue());
    }

    /**
     * The period a user asked for, refused with a message key rather than an exception the
     * screen has to translate itself.
     */
    public static PayrollPeriod parse(Integer year, Integer month) throws UserValidationException {
        if (year == null || month == null || year < 2000 || year > 2200 || month < 1 || month > 12) {
            throw new UserValidationException("payroll.error.period");
        }
        return new PayrollPeriod(year, month);
    }

    public LocalDate firstDay() {
        return LocalDate.of(year, month, 1);
    }

    /** The day the entitlement is dated: a month is earned by the time it ends. */
    public LocalDate lastDay() {
        return YearMonth.of(year, month).atEndOfMonth();
    }

    /** 28, 29, 30 or 31 - never a constant. */
    public int lengthInDays() {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    /** Whether a date falls inside this period. */
    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(firstDay()) && !date.isAfter(lastDay());
    }

    /**
     * How many days of this period an employee was actually on the books.
     * <p>
     * Somebody hired on the 20th of a 30-day month is owed ten days, not a month, and
     * somebody who left on the 10th is owed ten. Both cases are ordinary and both were
     * silently full months in every hand-kept sheet this replaces.
     *
     * @param hired the hire date, or null if unknown
     * @param ended the last working day, or null if still employed
     */
    public int employedDays(LocalDate hired, LocalDate ended) {
        LocalDate from = hired != null && hired.isAfter(firstDay()) ? hired : firstDay();
        LocalDate to = ended != null && ended.isBefore(lastDay()) ? ended : lastDay();
        if (to.isBefore(from)) {
            return 0;
        }
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    @Override
    public String toString() {
        return String.format("%04d-%02d", year, month);
    }
}
