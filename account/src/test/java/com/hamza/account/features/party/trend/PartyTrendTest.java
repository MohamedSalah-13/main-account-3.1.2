package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTrendTest {

    private static PartyTrendFilter firstQuarter(boolean compare) {
        return new PartyTrendFilter(PartyKind.CUSTOMER, TrendGranularity.MONTH,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, compare);
    }

    private static PartyTrendDay day(int year, int month, int dayOfMonth, String debit, String credit) {
        return new PartyTrendDay(LocalDate.of(year, month, dayOfMonth),
                new BigDecimal(debit), new BigDecimal(credit));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void everyPeriodIsAPointEvenAQuietOne() {
        PartyTrend trend = PartyTrend.build(firstQuarter(false),
                List.of(day(2026, 1, 10, "100.00", "40.00")), List.of());

        assertEquals(List.of("2026-01", "2026-02", "2026-03"),
                trend.points().stream().map(PartyTrendPoint::label).toList());
        assertEquals(money("0.00"), trend.points().get(1).debit(),
                "a month with no movement is a zero on the line, not a gap in it");
    }

    @Test
    void aDayIsFiledIntoItsOwnPeriod() {
        PartyTrend trend = PartyTrend.build(firstQuarter(false), List.of(
                day(2026, 1, 1, "100.00", "0.00"),
                day(2026, 1, 31, "50.00", "20.00"),
                day(2026, 2, 1, "0.00", "70.00")), List.of());

        assertEquals(money("150.00"), trend.points().get(0).debit());
        assertEquals(money("20.00"), trend.points().get(0).credit());
        assertEquals(money("70.00"), trend.points().get(1).credit());
        assertEquals(money("130.00"), trend.points().get(0).net());
    }

    @Test
    void theLastPointEndsWhereTheRangeDoes() {
        PartyTrendFilter filter = new PartyTrendFilter(PartyKind.CUSTOMER, TrendGranularity.MONTH,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 11), null, false);

        PartyTrend trend = PartyTrend.build(filter, List.of(), List.of());

        assertEquals(LocalDate.of(2026, 8, 31), trend.points().get(0).end());
        assertEquals(LocalDate.of(2026, 9, 11), trend.points().get(1).end());
    }

    @Test
    void theYearBeforeIsFiledUnderTheSameDatesThisYear() {
        PartyTrend trend = PartyTrend.build(firstQuarter(true),
                List.of(day(2026, 2, 14, "300.00", "100.00")),
                List.of(day(2025, 2, 14, "200.00", "150.00")));

        PartyTrendPoint february = trend.points().get(1);
        assertEquals(money("200.00"), february.previousDebit());
        assertEquals(money("150.00"), february.previousCredit());
        assertEquals(money("300.00"), february.debit());
    }

    @Test
    void withoutAComparisonTheYearBeforeIsIgnored() {
        PartyTrend trend = PartyTrend.build(firstQuarter(false), List.of(),
                List.of(day(2025, 2, 14, "200.00", "150.00")));

        assertEquals(money("0.00"), trend.points().get(1).previousDebit());
        assertTrue(trend.isEmpty());
    }

    /** A day the query should not have returned is not filed anywhere, rather than misfiled. */
    @Test
    void aDayOutsideTheRangeIsNotCounted() {
        PartyTrend trend = PartyTrend.build(firstQuarter(false),
                List.of(day(2026, 4, 1, "999.00", "0.00")), List.of());

        assertEquals(money("0.00"), trend.summary().debit());
    }

    @Test
    void theTotalsAreTheSumOfThePoints() {
        PartyTrend trend = PartyTrend.build(firstQuarter(true), List.of(
                day(2026, 1, 5, "400.00", "100.00"),
                day(2026, 3, 5, "100.00", "300.00")), List.of(
                day(2025, 1, 5, "250.00", "200.00")));

        PartyTrendSummary summary = trend.summary();
        assertEquals(money("500.00"), summary.debit());
        assertEquals(money("400.00"), summary.credit());
        assertEquals(money("100.00"), summary.net());
        assertEquals(Optional.of(money("80.00")), summary.collectionPercent());
        assertEquals(Optional.of(money("100.00")), summary.debitChange());
        assertEquals(Optional.of(money("100.00")), summary.creditChange());
        assertFalse(trend.isEmpty());
    }

    /** A percentage of nothing is absent, not zero - and not a hundred. */
    @Test
    void aPercentageWithNothingToDivideByIsAbsent() {
        PartyTrend trend = PartyTrend.build(firstQuarter(true),
                List.of(day(2026, 1, 5, "0.00", "100.00")), List.of());

        assertEquals(Optional.empty(), trend.summary().collectionPercent());
        assertEquals(Optional.empty(), trend.summary().creditChange());
        assertEquals(Optional.empty(), PartyTrend.build(firstQuarter(false),
                List.of(day(2026, 1, 5, "10.00", "10.00")), List.of()).summary().debitChange());
    }
}
