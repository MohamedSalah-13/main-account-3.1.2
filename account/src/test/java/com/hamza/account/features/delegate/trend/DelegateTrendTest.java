package com.hamza.account.features.delegate.trend;

import com.hamza.account.features.party.trend.TrendGranularity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a delegate's days become periods - {@link DelegateTrend}, {@link DelegateTrendSummary}, the filter. */
class DelegateTrendTest {

    private static final int DELEGATE = 4;

    private static DelegateTrendDay day(String date, String sales, String returns, String collected) {
        return new DelegateTrendDay(LocalDate.parse(date), new BigDecimal(sales), new BigDecimal(returns),
                new BigDecimal(collected));
    }

    private static DelegateTrendFilter months(String from, String to, boolean compare) {
        return new DelegateTrendFilter(DELEGATE, TrendGranularity.MONTH, LocalDate.parse(from),
                LocalDate.parse(to), compare);
    }

    @Test
    @DisplayName("days are filed into their month, and a quiet month is a point of zeros")
    void daysAreFiledIntoTheirMonth() {
        DelegateTrend trend = DelegateTrend.build(months("2026-06-01", "2026-08-31", false), List.of(
                day("2026-06-03", "1000", "0", "400"),
                day("2026-06-20", "500", "100", "300"),
                day("2026-08-15", "250", "0", "250")), List.of());

        assertEquals(3, trend.points().size());
        DelegateTrendPoint june = trend.points().getFirst();
        assertEquals("2026-06", june.label());
        assertEquals(0, new BigDecimal("1400").compareTo(june.netSales()), "1500 sold less 100 returned");
        assertEquals(0, new BigDecimal("700").compareTo(june.collected()));
        DelegateTrendPoint july = trend.points().get(1);
        assertEquals(0, july.sales().signum(), "July moved nothing and is still on the chart");
        assertEquals(0, july.collected().signum());
    }

    /** A return is on its own day, as in the performance report: it may leave a month negative. */
    @Test
    void aMonthOfReturnsIsNegative() {
        DelegateTrend trend = DelegateTrend.build(months("2026-07-01", "2026-07-31", false),
                List.of(day("2026-07-10", "0", "300", "-300")), List.of());

        assertEquals(0, new BigDecimal("-300").compareTo(trend.points().getFirst().netSales()));
        assertEquals(0, new BigDecimal("-300").compareTo(trend.summary().collected()));
    }

    @Test
    @DisplayName("the year before is the same dates, filed into this year's months")
    void theYearBefore() {
        DelegateTrend trend = DelegateTrend.build(months("2026-06-01", "2026-07-31", true),
                List.of(day("2026-06-10", "1000", "0", "500")),
                List.of(day("2025-06-10", "800", "50", "600"), day("2025-07-31", "100", "0", "0")));

        assertEquals(0, new BigDecimal("750").compareTo(trend.points().getFirst().previousNetSales()));
        assertEquals(0, new BigDecimal("600").compareTo(trend.points().getFirst().previousCollected()));
        assertEquals(0, new BigDecimal("100").compareTo(trend.points().get(1).previousNetSales()));
        assertEquals(0, new BigDecimal("850").compareTo(trend.summary().previousNetSales()),
                "750 in June and 100 on 31 July, a year back");
        assertEquals(0, new BigDecimal("17.65").compareTo(trend.summary().netSalesChange().orElseThrow()),
                "1000 against 850 last year");
    }

    @Test
    @DisplayName("a percentage with nothing to divide by is absent, never zero")
    void nothingToDivideByIsAbsent() {
        DelegateTrend quiet = DelegateTrend.build(months("2026-06-01", "2026-06-30", true), List.of(), List.of());

        assertTrue(quiet.isEmpty());
        assertTrue(quiet.summary().collectionPercent().isEmpty());
        assertTrue(quiet.summary().netSalesChange().isEmpty());

        DelegateTrend sold = DelegateTrend.build(months("2026-06-01", "2026-06-30", false),
                List.of(day("2026-06-02", "1000", "0", "250")), List.of());
        assertEquals(0, new BigDecimal("25.00").compareTo(sold.summary().collectionPercent().orElseThrow()));
        assertTrue(sold.summary().netSalesChange().isEmpty(), "no comparison was asked for");
    }

    /** The week is the party trend's - it starts on Saturday - because the periods are one definition. */
    @Test
    void theWeekStartsOnSaturday() {
        DelegateTrend trend = DelegateTrend.build(new DelegateTrendFilter(DELEGATE, TrendGranularity.WEEK,
                        LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-18"), false),
                List.of(day("2026-09-11", "100", "0", "0"), day("2026-09-12", "200", "0", "0")), List.of());

        assertEquals(2, trend.points().size());
        assertEquals(0, new BigDecimal("100").compareTo(trend.points().getFirst().sales()), "Friday closes the week");
        assertEquals(0, new BigDecimal("200").compareTo(trend.points().get(1).sales()), "Saturday opens the next");
    }

    @Test
    void theFilterRefusesWhatCannotBeCharted() {
        assertThrows(IllegalArgumentException.class, () -> months("2026-08-01", "2026-07-01", false));
        assertThrows(IllegalArgumentException.class, () -> new DelegateTrendFilter(0, TrendGranularity.MONTH,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), false));
        assertThrows(IllegalArgumentException.class, () -> new DelegateTrendFilter(DELEGATE, TrendGranularity.WEEK,
                LocalDate.parse("2020-01-01"), LocalDate.parse("2026-01-01"), false));
        assertFalse(new DelegateTrendFilter(DELEGATE, TrendGranularity.YEAR, LocalDate.parse("2022-01-01"),
                LocalDate.parse("2026-01-01"), true).compareWithPreviousYear(), "by year, last year is the point beside");
    }
}
