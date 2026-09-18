package com.hamza.account.features.expense.recurring;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One period of one template that has fallen due and has nothing recorded against it.
 *
 * @param periodStart the first day of that period
 * @param dueOn       the day inside it the template is due - the 31st clamped to a short month's end
 * @param daysLate    how many days have passed since it fell due; zero on the day itself
 */
public record ExpenseRecurringDue(ExpenseRecurring template, LocalDate periodStart, LocalDate dueOn, long daysLate) {

    public ExpenseRecurringDue {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(dueOn, "dueOn");
    }

    /** The last day of the period, which is what "recorded in this period" is measured over. */
    public LocalDate periodEnd() {
        return template.frequency().next(periodStart).minusDays(1);
    }
}
