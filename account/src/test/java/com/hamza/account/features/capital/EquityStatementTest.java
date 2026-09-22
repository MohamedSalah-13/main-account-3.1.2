package com.hamza.account.features.capital;

import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.features.profitloss.ProfitLossRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityStatementTest {

    private static final CapitalFilter Q1 = new CapitalFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static CapitalDay day(String date, int treasury, String name, String in, String out) {
        return new CapitalDay(LocalDate.parse(date), treasury, name, d(in), d(out), 1);
    }

    private static ProfitLossRow profit(String date, String net) {
        return new ProfitLossRow(LocalDate.parse(date), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, d(net));
    }

    /**
     * Openings of 1,000 in the drawer, 400 owed by customers, 150 owed to suppliers and 250 of stock;
     * before the year 3,000 paid in, 500 drawn and 1,200 earned. In the quarter 5,000 paid in, 800
     * drawn and 900 earned.
     */
    private static EquityStatement statement() {
        return new EquityStatement(Q1,
                new BroughtForward(d("1000"), d("400"), d("150"), d("250")),
                new CapitalBefore(d("3000"), d("500")),
                d("1200"),
                List.of(day("2026-01-10", 1, "الخزينة", "5000", "0"),
                        day("2026-03-05", 2, "فودافون كاش", "0", "800")),
                List.of(profit("2026-01-15", "600"), profit("2026-03-20", "300")));
    }

    @Test
    void theStatementAddsUp() {
        EquityStatement statement = statement();

        assertEquals(d("1500"), statement.broughtForward().total(), "1000 + 400 - 150 + 250");
        assertEquals(d("5200"), statement.opening(), "1500 + (3000 - 500) + 1200");
        assertEquals(d("5000"), statement.paidIn());
        assertEquals(d("800"), statement.drawn());
        assertEquals(d("900"), statement.profit());
        assertEquals(d("10300"), statement.closing(), "5200 + 5000 - 800 + 900");
        assertEquals(2, statement.movements());
    }

    @Test
    void aRowPerTreasuryTheLargestNetFirst() {
        List<CapitalByTreasuryRow> rows = statement().byTreasury();

        assertEquals(List.of(1, 2), rows.stream().map(CapitalByTreasuryRow::treasuryId).toList());
        assertEquals(d("5000"), rows.get(0).net());
        assertEquals(d("-800"), rows.get(1).net());
    }

    /** February holds nothing, and equity stands still through it rather than vanishing from the line. */
    @Test
    void eachPeriodClosesWhereTheRunningEquityStands() {
        List<EquityPeriod> months = statement().periods(TrendGranularity.MONTH);

        assertEquals(3, months.size());
        assertEquals(new EquityPeriod(LocalDate.of(2026, 1, 1), "2026-01", d("5000"), BigDecimal.ZERO, d("600"),
                d("10800")), months.get(0));
        assertEquals(d("10800"), months.get(1).closing());
        assertEquals(0, months.get(1).change().signum());
        assertEquals(d("10300"), months.get(2).closing(), "the last period closes on the statement's closing");
    }

    @Test
    void tooManyPeriodsAreRefusedNotDrawn() {
        EquityStatement decade = new EquityStatement(new CapitalFilter(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 3, 31)),
                BroughtForward.NONE, new CapitalBefore(BigDecimal.ZERO, BigDecimal.ZERO), BigDecimal.ZERO,
                List.of(), List.of());
        assertFalse(decade.canGroupBy(TrendGranularity.MONTH));
        assertTrue(decade.canGroupBy(TrendGranularity.YEAR));
        assertThrows(IllegalArgumentException.class, () -> decade.periods(TrendGranularity.MONTH));
    }

    @Test
    void aFilterThatExistsIsOneTheStatementCanAnswer() {
        assertEquals(CapitalFilter.Problem.REVERSED,
                CapitalFilter.problem(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(CapitalFilter.Problem.MISSING, CapitalFilter.problem(null, LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new CapitalFilter(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)));
        assertEquals(new CapitalFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 22)),
                CapitalFilter.yearToDate(LocalDate.of(2026, 9, 22)));
    }
}
