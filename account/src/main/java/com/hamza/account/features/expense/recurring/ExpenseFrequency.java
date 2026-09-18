package com.hamza.account.features.expense.recurring;

import java.time.LocalDate;

/**
 * How often a recurring expense falls due.
 * <p>
 * <b>The periods are counted here, in Java, and not in SQL</b> - the rule the collections trend set and
 * the expense reports kept. A quarter in MySQL is a {@code QUARTER()} expression nothing can ask a
 * question of; here {@code ExpenseFrequencyTest} can ask about February, a leap year and the 31st.
 */
public enum ExpenseFrequency {

    MONTHLY("expense.recurring.frequency.monthly", 1),
    QUARTERLY("expense.recurring.frequency.quarterly", 3),
    YEARLY("expense.recurring.frequency.yearly", 12);

    private final String messageKey;
    private final int months;

    ExpenseFrequency(String messageKey, int months) {
        this.messageKey = messageKey;
        this.months = months;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /** How many months one period spans. */
    public int months() {
        return months;
    }

    /**
     * The first day of the period {@code day} falls in, counted from the template's own start.
     * <p>
     * <b>A quarter starts where the template starts, not in January.</b> A rent agreed in February and
     * paid every three months falls due in February, May, August and November - a calendar quarter would
     * have made it due in January and left February looking unpaid.
     */
    public LocalDate periodStart(LocalDate start, LocalDate day) {
        LocalDate firstPeriod = start.withDayOfMonth(1);
        LocalDate asked = day.withDayOfMonth(1);
        if (asked.isBefore(firstPeriod)) {
            return firstPeriod;
        }
        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(firstPeriod, asked);
        return firstPeriod.plusMonths(monthsBetween - (monthsBetween % months));
    }

    /** The period after the one starting on {@code periodStart}. */
    public LocalDate next(LocalDate periodStart) {
        return periodStart.plusMonths(months);
    }

    /**
     * The day the template is due inside its period.
     * <p>
     * <b>The 31st of a short month is its last day</b>, never the 1st of the next one: a template due on
     * the 31st would otherwise skip February entirely, and a reminder that never appears is worse than
     * one a day early.
     */
    public LocalDate dueDate(LocalDate periodStart, int dayOfMonth) {
        int day = Math.min(Math.max(dayOfMonth, 1), periodStart.lengthOfMonth());
        return periodStart.withDayOfMonth(day);
    }
}
