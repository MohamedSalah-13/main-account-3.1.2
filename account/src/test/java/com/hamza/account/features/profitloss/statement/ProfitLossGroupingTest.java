package com.hamza.account.features.profitloss.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfitLossGroupingTest {

    @Test
    @DisplayName("a week runs Saturday to Friday, as on every statement here")
    void weeksStartOnSaturday() {
        LocalDate friday = LocalDate.of(2026, 9, 25);
        assertEquals(DayOfWeek.FRIDAY, friday.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 9, 19), ProfitLossGrouping.WEEK.start(friday));
        assertEquals(LocalDate.of(2026, 9, 26), ProfitLossGrouping.WEEK.start(friday.plusDays(1)));
        assertEquals(LocalDate.of(2026, 9, 26), ProfitLossGrouping.WEEK.next(LocalDate.of(2026, 9, 19)));
    }

    @Test
    void daysAndMonths() {
        LocalDate day = LocalDate.of(2028, 2, 29);
        assertEquals(day, ProfitLossGrouping.DAY.start(day));
        assertEquals(LocalDate.of(2028, 3, 1), ProfitLossGrouping.DAY.next(day));
        assertEquals(LocalDate.of(2028, 2, 1), ProfitLossGrouping.MONTH.start(day));
        assertEquals(LocalDate.of(2028, 3, 1), ProfitLossGrouping.MONTH.next(LocalDate.of(2028, 2, 1)));
    }

    @Test
    @DisplayName("a period opens a day to a row up to two months, a week up to half a year, then a month")
    void suited() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertEquals(ProfitLossGrouping.DAY, ProfitLossGrouping.suitedTo(new ProfitLossPeriod(from, from.plusDays(61))));
        assertEquals(ProfitLossGrouping.WEEK, ProfitLossGrouping.suitedTo(new ProfitLossPeriod(from, from.plusDays(62))));
        assertEquals(ProfitLossGrouping.WEEK, ProfitLossGrouping.suitedTo(new ProfitLossPeriod(from, from.plusDays(189))));
        assertEquals(ProfitLossGrouping.MONTH, ProfitLossGrouping.suitedTo(new ProfitLossPeriod(from, from.plusDays(190))));
    }
}
