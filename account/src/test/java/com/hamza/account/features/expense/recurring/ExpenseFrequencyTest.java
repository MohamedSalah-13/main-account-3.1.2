package com.hamza.account.features.expense.recurring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The period arithmetic, asked about the months that are not thirty days long.
 * <p>
 * It is in Java rather than in SQL so these questions can be asked at all: a {@code QUARTER()}
 * expression cannot be asked what February means to a template that started in February.
 */
class ExpenseFrequencyTest {

    @Test
    @DisplayName("a month's period is its own first day, whatever day of it is asked about")
    void monthlyPeriods() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        assertEquals(LocalDate.of(2026, 3, 1),
                ExpenseFrequency.MONTHLY.periodStart(start, LocalDate.of(2026, 3, 27)));
        assertEquals(LocalDate.of(2026, 3, 1),
                ExpenseFrequency.MONTHLY.periodStart(start, LocalDate.of(2026, 3, 1)));
    }

    @Test
    @DisplayName("a quarter runs from the template's own start, not from January")
    void quartersStartWithTheTemplate() {
        LocalDate february = LocalDate.of(2026, 2, 1);
        // February, May, August, November - a calendar quarter would answer January and leave February
        // looking unpaid on a rent that is actually paid in it.
        assertEquals(february, ExpenseFrequency.QUARTERLY.periodStart(february, LocalDate.of(2026, 4, 30)));
        assertEquals(LocalDate.of(2026, 5, 1),
                ExpenseFrequency.QUARTERLY.periodStart(february, LocalDate.of(2026, 5, 1)));
        assertEquals(LocalDate.of(2026, 11, 1),
                ExpenseFrequency.QUARTERLY.periodStart(february, LocalDate.of(2027, 1, 31)));
    }

    @Test
    @DisplayName("a year runs from the template's own month too")
    void yearsStartWithTheTemplate() {
        LocalDate june = LocalDate.of(2025, 6, 1);
        assertEquals(june, ExpenseFrequency.YEARLY.periodStart(june, LocalDate.of(2026, 5, 31)));
        assertEquals(LocalDate.of(2026, 6, 1),
                ExpenseFrequency.YEARLY.periodStart(june, LocalDate.of(2026, 6, 1)));
    }

    @Test
    @DisplayName("a day before the template started is its first period, never one before it")
    void nothingBeforeTheStart() {
        LocalDate start = LocalDate.of(2026, 6, 15);
        assertEquals(LocalDate.of(2026, 6, 1),
                ExpenseFrequency.MONTHLY.periodStart(start, LocalDate.of(2026, 1, 1)));
    }

    @Test
    @DisplayName("the 31st falls on a short month's last day rather than skipping the month")
    void shortMonths() {
        assertEquals(LocalDate.of(2026, 2, 28),
                ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2026, 2, 1), 31));
        assertEquals(LocalDate.of(2028, 2, 29),
                ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2028, 2, 1), 31), "a leap February");
        assertEquals(LocalDate.of(2026, 4, 30),
                ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2026, 4, 1), 31));
        assertEquals(LocalDate.of(2026, 3, 31),
                ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2026, 3, 1), 31));
    }

    @Test
    @DisplayName("a day outside the month is clamped rather than thrown out of")
    void impossibleDays() {
        assertEquals(LocalDate.of(2026, 3, 1), ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2026, 3, 1), 0));
        assertEquals(LocalDate.of(2026, 3, 31), ExpenseFrequency.MONTHLY.dueDate(LocalDate.of(2026, 3, 1), 99));
    }

    @Test
    @DisplayName("the next period is one span on, and a span is what the frequency says")
    void nextPeriod() {
        LocalDate march = LocalDate.of(2026, 3, 1);
        assertEquals(LocalDate.of(2026, 4, 1), ExpenseFrequency.MONTHLY.next(march));
        assertEquals(LocalDate.of(2026, 6, 1), ExpenseFrequency.QUARTERLY.next(march));
        assertEquals(LocalDate.of(2027, 3, 1), ExpenseFrequency.YEARLY.next(march));
        assertEquals(1, ExpenseFrequency.MONTHLY.months());
        assertEquals(3, ExpenseFrequency.QUARTERLY.months());
        assertEquals(12, ExpenseFrequency.YEARLY.months());
    }
}
