package com.hamza.account.features.report.monthly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hamza.account.features.report.monthly.MonthFiguresTest.figures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyTotalsReportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Test
    @DisplayName("a row per year from the first document to this one, newest first, a quiet year as zeros")
    void everyYearIsARow() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2023, 12, 5, "100.00"), day(2026, 2, 1, "40.00")), TODAY);

        assertEquals(List.of(2026, 2025, 2024, 2023), report.years().stream().map(YearRow::year).toList());
        YearRow quiet = report.years().get(1);
        assertEquals(12, quiet.lastMonth());
        assertEquals(BigDecimal.ZERO, quiet.total(MonthlyMeasure.NET));
    }

    @Test
    @DisplayName("the first year starts at its first document's month; every later year at January")
    void theFirstYearStartsAtItsFirstDocument() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2024, 6, 3, "10.00"), day(2025, 8, 1, "20.00")), TODAY);

        YearRow first = report.years().getLast();
        assertEquals(6, first.firstMonth());
        assertNull(first.value(MonthlyMeasure.NET, 5), "May 2024 is before anything was recorded");
        assertEquals(1, report.years().get(1).firstMonth(), "2025 is a whole year, its quiet months zeros");
        assertEquals(BigDecimal.ZERO, report.years().get(1).value(MonthlyMeasure.NET, 1));
    }

    @Test
    @DisplayName("the running year stops at this month; a year that is over has twelve")
    void theRunningYearStopsAtThisMonth() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2025, 3, 10, "100.00"), day(2026, 1, 15, "40.00")), TODAY);

        assertEquals(9, report.years().getFirst().lastMonth());
        assertNull(report.years().getFirst().value(MonthlyMeasure.NET, 10), "October has not come");
        assertEquals(12, report.years().get(1).lastMonth());
    }

    @Test
    @DisplayName("a document already dated later this year carries the year to its month")
    void aLaterDocumentThisYearExtendsTheYear() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2026, 11, 2, "70.00")), TODAY);

        assertEquals(11, report.years().getFirst().lastMonth());
        assertEquals(new BigDecimal("70.00"), report.years().getFirst().value(MonthlyMeasure.NET, 11));
    }

    @Test
    @DisplayName("the days of a month are added into it, and two entries for one day are both counted")
    void daysAreFoldedIntoMonths() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2026, 3, 1, "10.00"), day(2026, 3, 31, "20.00"), day(2026, 3, 31, "5.00"),
                new DayFigures(LocalDate.of(2026, 3, 2), figures(0, "0", "0", 1, "8.00", "3.00"))), TODAY);

        YearRow year = report.years().getFirst();
        assertEquals(new BigDecimal("30.00"), year.value(MonthlyMeasure.NET, 3), "35 sold less 5 given back");
        assertEquals(BigDecimal.valueOf(3), year.value(MonthlyMeasure.INVOICES, 3));
        assertEquals(new BigDecimal("5.00"), year.value(MonthlyMeasure.RETURNS, 3));
    }

    @Test
    @DisplayName("this year so far is set against last year to the same date, not against its whole month")
    void theComparisonIsByTheDay() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2025, 9, 23, "100.00"), day(2025, 9, 24, "900.00"), day(2025, 12, 31, "5.00"),
                day(2026, 1, 1, "30.00"), day(2026, 9, 23, "20.00")), TODAY);

        assertEquals(new BigDecimal("50.00"), report.yearToDate().net());
        assertEquals(LocalDate.of(2025, 9, 23), report.sameDayLastYear());
        assertEquals(new BigDecimal("100.00"), report.sameDaysLastYear().net(), "the 24th is after the same date");
        assertEquals(new BigDecimal("1005.00"), report.previousYear().net());
    }

    @Test
    @DisplayName("the 29th of February is compared with the 28th")
    void aLeapDay() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2023, 2, 28, "7.00"), day(2023, 3, 1, "9.00")), LocalDate.of(2024, 2, 29));

        assertEquals(LocalDate.of(2023, 2, 28), report.sameDayLastYear());
        assertEquals(new BigDecimal("7.00"), report.sameDaysLastYear().net());
    }

    @Test
    @DisplayName("a change from nothing is absent, not a percentage; otherwise it is measured on the previous size")
    void theChange() {
        assertEquals(Optional.empty(), MonthlyTotalsReport.change(new BigDecimal("50"), BigDecimal.ZERO));
        assertEquals(Optional.of(new BigDecimal("-50.00")),
                MonthlyTotalsReport.change(new BigDecimal("50"), new BigDecimal("100")));
        assertEquals(Optional.of(new BigDecimal("150.00")),
                MonthlyTotalsReport.change(new BigDecimal("50"), new BigDecimal("-100")),
                "from a net of -100 to 50 is a rise");
    }

    @Test
    @DisplayName("the highest month is this year's, and a year with nothing above zero has none")
    void theHighestMonth() {
        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2025, 6, 1, "999.00"), day(2026, 2, 1, "40.00"), day(2026, 7, 1, "90.00")), TODAY);

        assertEquals(Optional.of(7), report.highestMonth(MonthlyMeasure.NET), "last year's June is not this year's");
        assertEquals(Optional.empty(), report.highestMonth(MonthlyMeasure.DISCOUNT));
    }

    @Test
    @DisplayName("no document at all is an empty report, and the chart draws at most five years")
    void emptinessAndTheChart() {
        assertTrue(MonthlyTotalsReport.of(MonthlySide.PURCHASES, List.of(), TODAY).isEmpty());

        MonthlyTotalsReport report = MonthlyTotalsReport.of(MonthlySide.SALES, List.of(
                day(2018, 1, 1, "1.00")), TODAY);
        assertEquals(9, report.years().size());
        assertEquals(List.of(2026, 2025, 2024, 2023, 2022),
                report.chartYears().stream().map(YearRow::year).toList());
    }

    private static DayFigures day(int year, int month, int day, String gross) {
        return new DayFigures(LocalDate.of(year, month, day), figures(1, gross, "0", 0, "0", "0"));
    }
}
