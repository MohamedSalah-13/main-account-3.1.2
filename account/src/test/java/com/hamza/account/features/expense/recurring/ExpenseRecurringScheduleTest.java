package com.hamza.account.features.expense.recurring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a template is due, and when it has already been answered. */
class ExpenseRecurringScheduleTest {

    private static ExpenseRecurring template(int id, ExpenseFrequencyHolder holder) {
        return new ExpenseRecurring(id, 11, "إيجار", null, 1, "الخزينة", new BigDecimal("5000"), "المالك", "",
                holder.frequency, holder.dayOfMonth, holder.start, holder.end, holder.active);
    }

    /** A small builder, so each case says only what it changes. */
    private static final class ExpenseFrequencyHolder {
        ExpenseFrequency frequency = ExpenseFrequency.MONTHLY;
        int dayOfMonth = 1;
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = null;
        boolean active = true;
    }

    private static ExpenseFrequencyHolder monthly() {
        return new ExpenseFrequencyHolder();
    }

    @Test
    @DisplayName("due on its day, and not before it")
    void dueOnItsDay() {
        ExpenseFrequencyHolder holder = monthly();
        holder.dayOfMonth = 5;
        ExpenseRecurring rent = template(1, holder);

        assertTrue(ExpenseRecurringSchedule.due(List.of(rent), Map.of(), LocalDate.of(2026, 3, 4)).stream()
                .noneMatch(due -> due.periodStart().equals(LocalDate.of(2026, 3, 1))), "the 4th is too early");
        // Soonest first, so the grace period comes before the current one - March is the later entry.
        ExpenseRecurringDue march = ExpenseRecurringSchedule.due(List.of(rent), Map.of(), LocalDate.of(2026, 3, 5))
                .stream().filter(due -> due.periodStart().equals(LocalDate.of(2026, 3, 1))).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 5), march.dueOn());
        assertEquals(0, march.daysLate());
    }

    @Test
    @DisplayName("a period already recorded does not remind; the one before it still can")
    void recordedPeriodsAreQuiet() {
        ExpenseRecurring rent = template(1, monthly());
        LocalDate today = LocalDate.of(2026, 3, 10);

        List<ExpenseRecurringDue> both = ExpenseRecurringSchedule.due(List.of(rent), Map.of(), today);
        assertEquals(List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
                both.stream().map(ExpenseRecurringDue::periodStart).toList(),
                "the current period and the one before it - the grace a rent paid on the 3rd needs");

        List<ExpenseRecurringDue> marchOnly = ExpenseRecurringSchedule.due(List.of(rent),
                Map.of(1, Set.of(LocalDate.of(2026, 2, 1))), today);
        assertEquals(List.of(LocalDate.of(2026, 3, 1)),
                marchOnly.stream().map(ExpenseRecurringDue::periodStart).toList());

        assertTrue(ExpenseRecurringSchedule.due(List.of(rent),
                Map.of(1, Set.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1))), today).isEmpty());
    }

    @Test
    @DisplayName("a template entered with an old start date does not remind for every period since")
    void onlyTheCurrentPeriodAndOneBefore() {
        ExpenseFrequencyHolder holder = monthly();
        holder.start = LocalDate.of(2024, 1, 1);
        ExpenseRecurring rent = template(1, holder);

        assertEquals(ExpenseRecurringSchedule.GRACE_PERIODS + 1,
                ExpenseRecurringSchedule.due(List.of(rent), Map.of(), LocalDate.of(2026, 3, 10)).size(),
                "two years of history would otherwise be twenty-four reminders on the day it was saved");
    }

    @Test
    @DisplayName("the 31st of a short month falls on its last day rather than skipping the month")
    void shortMonthClampsRatherThanSkips() {
        ExpenseFrequencyHolder holder = monthly();
        holder.dayOfMonth = 31;
        ExpenseRecurring template = template(1, holder);

        List<ExpenseRecurringDue> february =
                ExpenseRecurringSchedule.due(List.of(template), Map.of(), LocalDate.of(2026, 2, 28));
        assertTrue(february.stream().anyMatch(due -> due.dueOn().equals(LocalDate.of(2026, 2, 28))));
    }

    @Test
    @DisplayName("a quarter runs from the template's own start, not from January")
    void quarterStartsWithTheTemplate() {
        ExpenseFrequencyHolder holder = monthly();
        holder.frequency = ExpenseFrequency.QUARTERLY;
        holder.start = LocalDate.of(2026, 2, 1);
        ExpenseRecurring template = template(1, holder);

        assertEquals(LocalDate.of(2026, 5, 1), template.periodStart(LocalDate.of(2026, 6, 15)),
                "February, May, August, November");
        List<ExpenseRecurringDue> due =
                ExpenseRecurringSchedule.due(List.of(template), Map.of(), LocalDate.of(2026, 5, 20));
        assertEquals(LocalDate.of(2026, 5, 1), due.get(due.size() - 1).periodStart());
    }

    @Test
    @DisplayName("a stopped template, one that has not started, and one that has ended say nothing")
    void quietTemplates() {
        ExpenseFrequencyHolder stopped = monthly();
        stopped.active = false;
        assertTrue(ExpenseRecurringSchedule.due(List.of(template(1, stopped)), Map.of(),
                LocalDate.of(2026, 3, 10)).isEmpty());

        ExpenseFrequencyHolder later = monthly();
        later.start = LocalDate.of(2026, 6, 1);
        assertTrue(ExpenseRecurringSchedule.due(List.of(template(2, later)), Map.of(),
                LocalDate.of(2026, 3, 10)).isEmpty());

        ExpenseFrequencyHolder ended = monthly();
        ended.end = LocalDate.of(2026, 1, 31);
        assertTrue(ExpenseRecurringSchedule.due(List.of(template(3, ended)), Map.of(),
                LocalDate.of(2026, 3, 10)).isEmpty());
    }

    @Test
    @DisplayName("how late it is, counted from the day it fell due")
    void daysLate() {
        ExpenseRecurring rent = template(1, monthly());
        List<ExpenseRecurringDue> due = ExpenseRecurringSchedule.due(List.of(rent),
                Map.of(1, Set.of(LocalDate.of(2026, 2, 1))), LocalDate.of(2026, 3, 8));

        assertEquals(7, due.get(0).daysLate());
        assertEquals(LocalDate.of(2026, 3, 31), due.get(0).periodEnd());
    }
}
