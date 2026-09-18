package com.hamza.account.features.expense.recurring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which templates are due today, and for which period - the whole decision behind the reminder, in one
 * place with no database and no JavaFX.
 * <p>
 * <b>"Due" means: the period has reached its day, and nothing recorded from this template falls inside
 * that period.</b> Recorded is a row of {@code expenses_details} carrying the template's id, which is
 * what {@code V66}'s {@code recurring_id} is for - not a row that merely looks similar. Guessing from
 * the heading and the amount would count a second rent paid in the same month as the first one's
 * reminder answered, and would leave a template whose amount changed reminding for ever.
 * <p>
 * <b>Only the current period reminds, never the ones before it.</b> A template entered with a start date
 * two years back would otherwise produce twenty-four reminders on the day it is saved. The period that
 * has just passed is offered once more - {@link #GRACE_PERIODS} - because a rent paid on the 3rd of the
 * next month is the ordinary case, not a missed one.
 */
public final class ExpenseRecurringSchedule {

    /** How many finished periods behind the current one may still remind. */
    public static final int GRACE_PERIODS = 1;

    private ExpenseRecurringSchedule() {
    }

    /**
     * The periods that are due and unrecorded, soonest first.
     *
     * @param templates the active templates
     * @param recorded  the period starts that already hold a row from that template, by template id
     * @param today     the day the question is asked on
     */
    public static List<ExpenseRecurringDue> due(List<ExpenseRecurring> templates,
                                                Map<Integer, Set<LocalDate>> recorded, LocalDate today) {
        List<ExpenseRecurringDue> due = new ArrayList<>();
        for (ExpenseRecurring template : templates) {
            if (!template.active()) {
                continue;
            }
            Set<LocalDate> done = recorded.getOrDefault(template.id(), Set.of());
            LocalDate period = template.periodStart(today);
            for (int back = 0; back <= GRACE_PERIODS; back++) {
                if (period.isBefore(template.startDate().withDayOfMonth(1))) {
                    break;
                }
                LocalDate dueOn = template.dueDate(period);
                // Not yet its day, or past the template's end: nothing to remind about for this period.
                boolean reached = !dueOn.isAfter(today);
                boolean withinEnd = template.endDate() == null || !dueOn.isAfter(template.endDate());
                if (reached && withinEnd && !done.contains(period)) {
                    due.add(new ExpenseRecurringDue(template, period, dueOn, ChronoUnit.DAYS.between(dueOn, today)));
                }
                period = period.minusMonths(template.frequency().months());
            }
        }
        due.sort(Comparator.comparing(ExpenseRecurringDue::dueOn)
                .thenComparing(item -> item.template().headingPath()));
        return due;
    }
}
