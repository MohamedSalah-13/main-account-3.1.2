package com.hamza.account.features.profitloss.statement;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitLossPeriodTest {

    private static ProfitLossPeriod period(String from, String to) {
        return new ProfitLossPeriod(LocalDate.parse(from), LocalDate.parse(to));
    }

    private static ProfitLossPeriod previous(String from, String to) {
        return period(from, to).previous(ComparisonBasis.PREVIOUS_PERIOD);
    }

    @Test
    @DisplayName("a month so far is set against the same days of the month before, not the 23 days before it")
    void monthToDate() {
        assertEquals(period("2026-08-01", "2026-08-23"), previous("2026-09-01", "2026-09-23"));
    }

    @Test
    @DisplayName("a whole month is set against the whole month before, whatever either's length")
    void wholeMonth() {
        assertEquals(period("2028-01-01", "2028-01-31"), previous("2028-02-01", "2028-02-29"));
        assertEquals(period("2026-02-01", "2026-02-28"), previous("2026-03-01", "2026-03-31"));
        assertEquals(period("2026-04-01", "2026-04-30"), previous("2026-05-01", "2026-05-31"));
    }

    @Test
    @DisplayName("the 31st has no twin in a shorter month: the period before ends at that month's end")
    void aDayTheMonthBeforeDoesNotHave() {
        assertEquals(period("2026-02-01", "2026-02-28"), previous("2026-03-01", "2026-03-30"));
    }

    @Test
    @DisplayName("a quarter is set against the quarter before, and a year so far against the months before it")
    void severalMonths() {
        assertEquals(period("2026-04-01", "2026-06-30"), previous("2026-07-01", "2026-09-30"));
        assertEquals(period("2025-04-01", "2025-12-23"), previous("2026-01-01", "2026-09-23"));
    }

    @Test
    @DisplayName("a period starting on any other day is set against as many days straight before it")
    void countedInDays() {
        assertEquals(period("2026-09-03", "2026-09-09"), previous("2026-09-10", "2026-09-16"));
        assertEquals(period("2026-09-22", "2026-09-22"), previous("2026-09-23", "2026-09-23"));
        assertEquals(period("2026-02-26", "2026-03-01"), previous("2026-03-02", "2026-03-05"));
    }

    @Test
    @DisplayName("the same period last year is the same dates, the 29th of February landing on the 28th")
    void lastYear() {
        assertEquals(period("2025-09-01", "2025-09-23"),
                period("2026-09-01", "2026-09-23").previous(ComparisonBasis.SAME_PERIOD_LAST_YEAR));
        assertEquals(period("2027-02-01", "2027-02-28"),
                period("2028-02-01", "2028-02-29").previous(ComparisonBasis.SAME_PERIOD_LAST_YEAR));
    }

    @Test
    @DisplayName("a period longer than a year goes back whole years until it no longer overlaps itself")
    void longerThanAYear() {
        assertEquals(period("2024-01-01", "2025-01-13"),
                period("2026-01-01", "2027-01-13").previous(ComparisonBasis.SAME_PERIOD_LAST_YEAR));
        assertEquals(period("2025-01-01", "2025-12-31"),
                period("2026-01-01", "2026-12-31").previous(ComparisonBasis.SAME_PERIOD_LAST_YEAR));
    }

    @Test
    @DisplayName("the period before always ends before this one starts")
    void neverOverlaps() {
        for (int day = 1; day <= 28; day++) {
            for (int length = 0; length < 400; length += 13) {
                LocalDate from = LocalDate.of(2026, 1, day);
                ProfitLossPeriod period = new ProfitLossPeriod(from, from.plusDays(length));
                for (ComparisonBasis basis : ComparisonBasis.values()) {
                    assertTrue(period.previous(basis).to().isBefore(from), period + " " + basis);
                }
            }
        }
    }

    @Test
    @DisplayName("a period typed in empty or the wrong way round is refused in words")
    void typedPeriods() {
        LocalDate day = LocalDate.of(2026, 9, 1);
        assertThrows(UserValidationException.class, () -> ProfitLossPeriod.of(null, day));
        assertThrows(UserValidationException.class, () -> ProfitLossPeriod.of(day, null));
        assertThrows(UserValidationException.class, () -> ProfitLossPeriod.of(day, day.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new ProfitLossPeriod(day, day.minusDays(1)));
    }

    @Test
    void daysAndMembership() throws Exception {
        ProfitLossPeriod period = ProfitLossPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertEquals(30, period.days());
        assertTrue(period.contains(LocalDate.of(2026, 9, 1)));
        assertTrue(period.contains(LocalDate.of(2026, 9, 30)));
        assertFalse(period.contains(LocalDate.of(2026, 10, 1)));
    }
}
