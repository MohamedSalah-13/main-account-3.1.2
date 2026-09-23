package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.account.features.report.monthly.MonthFiguresTest.figures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YearRowTest {

    @Test
    @DisplayName("a year still running has no figure for a month it has not reached - not a zero")
    void aMonthNotReachedIsEmpty() {
        YearRow row = new YearRow(2026, List.of(figures(1, "100.00", "0", 0, "0", "0"),
                MonthFigures.ZERO, figures(2, "50.00", "5.00", 0, "0", "0")));

        assertEquals(3, row.lastMonth());
        assertEquals(BigDecimal.ZERO, row.value(MonthlyMeasure.NET, 2), "a quiet month that has passed is a zero");
        assertNull(row.value(MonthlyMeasure.NET, 4));
        assertNull(row.month(12));
    }

    @Test
    @DisplayName("the year's total is its months added up, under any measure")
    void theTotalAddsTheMonths() {
        YearRow row = new YearRow(2025, List.of(figures(1, "100.00", "10.00", 0, "0", "0"),
                figures(2, "50.00", "0", 1, "20.00", "0")));

        assertEquals(new BigDecimal("120.00"), row.total(MonthlyMeasure.NET));
        assertEquals(BigDecimal.valueOf(3), row.total(MonthlyMeasure.INVOICES));
    }

    @Test
    @DisplayName("a year holds one to twelve months, and a month is 1-12")
    void theBounds() {
        assertThrows(IllegalArgumentException.class, () -> new YearRow(2026, List.of()));
        YearRow row = new YearRow(2026, List.of(MonthFigures.ZERO));
        assertThrows(IllegalArgumentException.class, () -> row.month(0));
        assertThrows(IllegalArgumentException.class, () -> row.month(13));
    }

    @Test
    @DisplayName("the first year is read from its first document's month: before it is empty, not a zero")
    void theFirstYearStartsAtItsFirstMonth() {
        YearRow row = new YearRow(2024, 6, List.of(MonthFigures.ZERO, MonthFigures.ZERO, MonthFigures.ZERO,
                MonthFigures.ZERO, figures(1, "999.00", "0", 0, "0", "0"), figures(1, "30.00", "0", 0, "0", "0")));

        assertNull(row.value(MonthlyMeasure.NET, 5), "a month before the first is not read, whatever it holds");
        assertEquals(new BigDecimal("30.00"), row.value(MonthlyMeasure.NET, 6));
        assertEquals(new BigDecimal("30.00"), row.total(MonthlyMeasure.NET), "and not added into the year");
        assertThrows(IllegalArgumentException.class, () -> new YearRow(2024, 7, List.of(MonthFigures.ZERO)));
    }
}
