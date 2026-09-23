package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The small records of the package: what each adds up to, and what each treats as nothing. */
class ProfitLossRecordsTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("net sales are gross less discounts less returns, and the cost is sold less returned")
    void salesBreakdown() {
        SalesBreakdown breakdown = new SalesBreakdown(money("1000"), money("40"), money("60"), money("650"),
                money("50"));
        assertEquals(money("900"), breakdown.netSales());
        assertEquals(money("600"), breakdown.costOfSales());
        assertEquals(BigDecimal.ZERO, new SalesBreakdown(null, null, null, null, null).netSales());
    }

    @Test
    @DisplayName("what is outside the profit is kept as sizes, and nets surpluses against shortages")
    void outsideTheProfit() {
        OutsideProfitFigures figures = new OutsideProfitFigures(money("-30"), money("5"), money("10"), null);
        assertEquals(money("30"), figures.stockShortage(), "a shortage read negative is still a shortage of 30");
        assertEquals(money("-35"), figures.net());
        assertFalse(figures.isEmpty());
        assertTrue(OutsideProfitFigures.ZERO.isEmpty());
    }

    @Test
    @DisplayName("a movement's profit is its sales less its cost less its expense")
    void movement() {
        ProfitLossMovement sale = new ProfitLossMovement(ProfitLossMovement.Kind.SALE, 12, LocalDate.of(2026, 9, 1),
                "علي", null, money("300"), money("200"), null);
        ProfitLossMovement expense = new ProfitLossMovement(ProfitLossMovement.Kind.EXPENSE, 4,
                LocalDate.of(2026, 9, 1), "إيجار", "سبتمبر", null, null, money("50"));
        assertEquals(money("100"), sale.profit());
        assertEquals(money("-50"), expense.profit());
        assertEquals("", sale.note());
    }

    @Test
    @DisplayName("an expense heading with no name reads as empty, not as null")
    void headingTotal() {
        assertEquals("", new ExpenseHeadingTotal(3, null, null).name());
        assertEquals(BigDecimal.ZERO, new ExpenseHeadingTotal(3, null, null).amount());
    }

    @Test
    @DisplayName("a report compares against the period before, and says when there is nothing in either")
    void report() {
        ProfitLossPeriod period = new ProfitLossPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        ProfitLossFigures now = new ProfitLossFigures(money("110"), money("60"), money("50"), money("10"),
                money("40"));
        ProfitLossFigures before = new ProfitLossFigures(money("100"), money("50"), money("50"), money("20"),
                money("30"));
        ProfitLossReport report = new ProfitLossReport(period, ComparisonBasis.PREVIOUS_PERIOD,
                period.previous(ComparisonBasis.PREVIOUS_PERIOD), ProfitLossGrouping.DAY, List.of(), now, before,
                List.of(), OutsideProfitFigures.ZERO);

        assertEquals(Optional.of(money("10.00")), report.netSalesChange());
        assertEquals(Optional.of(money("-50.00")), report.expensesChange());
        assertEquals(Optional.of(money("33.33")), report.netProfitChange());
        assertTrue(report.hasPrevious());
        assertFalse(report.isEmpty());

        ProfitLossReport empty = new ProfitLossReport(period, ComparisonBasis.PREVIOUS_PERIOD,
                period.previous(ComparisonBasis.PREVIOUS_PERIOD), ProfitLossGrouping.DAY, List.of(),
                ProfitLossFigures.ZERO, ProfitLossFigures.ZERO, List.of(), OutsideProfitFigures.ZERO);
        assertTrue(empty.isEmpty());
        assertFalse(empty.hasPrevious());
    }

    @Test
    void periodRow() {
        LocalDate day = LocalDate.of(2026, 9, 1);
        ProfitLossPeriodRow row = new ProfitLossPeriodRow(day, day, ProfitLossFigures.ZERO, List.of());
        assertTrue(row.isOneDay());
        assertFalse(row.hasActivity());
    }
}
