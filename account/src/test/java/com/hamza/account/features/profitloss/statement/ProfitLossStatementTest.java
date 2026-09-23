package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitLossStatementTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** Net sales 900, cost 600, gross 300, expenses 120, net 180. */
    private static ProfitLossFigures totals(String netSales, String cost, String expenses) {
        BigDecimal sales = money(netSales);
        BigDecimal costOfSales = money(cost);
        BigDecimal spent = money(expenses);
        BigDecimal gross = sales.subtract(costOfSales);
        return new ProfitLossFigures(sales, costOfSales, gross, spent, gross.subtract(spent));
    }

    private static ProfitLossStatement.Side side(ProfitLossFigures totals, SalesBreakdown sales,
                                                 List<ExpenseHeadingTotal> expenses, OutsideProfitFigures outside) {
        return new ProfitLossStatement.Side(totals, sales, expenses, outside);
    }

    private static final ProfitLossStatement.Side CURRENT = side(totals("900", "600", "120"),
            new SalesBreakdown(money("1000"), money("40"), money("60"), money("650"), money("50")),
            List.of(new ExpenseHeadingTotal(3, "إيجار", money("100")), new ExpenseHeadingTotal(7, "كهرباء", money("20"))),
            new OutsideProfitFigures(money("30"), money("5"), money("10"), BigDecimal.ZERO));

    private static final ProfitLossStatement.Side PREVIOUS = side(totals("500", "300", "80"),
            new SalesBreakdown(money("500"), BigDecimal.ZERO, BigDecimal.ZERO, money("300"), BigDecimal.ZERO),
            List.of(new ExpenseHeadingTotal(3, "إيجار", money("50")), new ExpenseHeadingTotal(9, "نقل", money("30"))),
            OutsideProfitFigures.ZERO);

    private static String captions(List<StatementLine> lines) {
        return lines.stream().map(line -> line.name() != null ? line.name() : line.messageKey())
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("the page reads revenue, cost, gross profit, expenses by heading, net profit, then what is outside it")
    void theOrderOfThePage() {
        assertEquals(String.join("\n",
                "profitloss.section.revenue",
                "profitloss.line.gross.sales", "profitloss.line.invoice.discounts", "profitloss.line.returns",
                "profitloss.net.sales",
                "profitloss.section.cost",
                "profitloss.line.cost.sold", "profitloss.line.cost.returned",
                "profitloss.cost.sales",
                "profitloss.gross.profit",
                "profitloss.section.expenses",
                "إيجار", "كهرباء", "نقل",
                "profitloss.line.expenses.total",
                "profitloss.net.profit",
                "profitloss.section.outside",
                "profitloss.line.stock.shortage", "profitloss.line.stock.surplus",
                "profitloss.line.till.shortage", "profitloss.line.till.surplus",
                "profitloss.line.outside.net"), captions(ProfitLossStatement.lines(CURRENT, PREVIOUS)));
    }

    @Test
    @DisplayName("each section adds up to its subtotal and each result to the subtotals before it")
    void itAddsUpDownThePage() {
        List<StatementLine> lines = ProfitLossStatement.lines(CURRENT, PREVIOUS);
        BigDecimal section = BigDecimal.ZERO;
        BigDecimal results = BigDecimal.ZERO;
        for (StatementLine line : lines) {
            switch (line.kind()) {
                case HEADING -> section = BigDecimal.ZERO;
                case ITEM -> section = section.add(line.current());
                case SUBTOTAL -> {
                    assertEquals(0, section.compareTo(line.current()), line.messageKey());
                    if (!"profitloss.line.outside.net".equals(line.messageKey())) {
                        results = results.add(line.current());
                    }
                }
                case RESULT -> assertEquals(0, results.compareTo(line.current()), line.messageKey());
            }
        }
        assertEquals(0, money("180").compareTo(results));
    }

    @Test
    @DisplayName("a deduction is negative, and the subtotals are the statement's own figures")
    void signs() {
        List<StatementLine> lines = ProfitLossStatement.lines(CURRENT, PREVIOUS);
        assertEquals(money("-40"), line(lines, "profitloss.line.invoice.discounts").current());
        assertEquals(money("-60"), line(lines, "profitloss.line.returns").current());
        assertEquals(money("-650"), line(lines, "profitloss.line.cost.sold").current());
        assertEquals(money("50"), line(lines, "profitloss.line.cost.returned").current());
        assertEquals(money("-600"), line(lines, "profitloss.cost.sales").current());
        assertEquals(money("300"), line(lines, "profitloss.gross.profit").current());
        assertEquals(money("-120"), line(lines, "profitloss.line.expenses.total").current());
        assertEquals(money("-80"), line(lines, "profitloss.line.expenses.total").previous());
        assertEquals(money("-35"), line(lines, "profitloss.line.outside.net").current());
    }

    @Test
    @DisplayName("an expense only the compared period had is still a line, at zero now")
    void aHeadingThatStopped() {
        StatementLine transport = ProfitLossStatement.lines(CURRENT, PREVIOUS).stream()
                .filter(line -> "نقل".equals(line.name())).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(transport.current()));
        assertEquals(money("-30"), transport.previous());
    }

    @Test
    @DisplayName("where the lines do not add up to the figure, a line says by how much rather than hiding it")
    void theUnexplained() {
        assertTrue(ProfitLossStatement.lines(CURRENT, PREVIOUS).stream()
                .noneMatch(line -> "profitloss.line.unexplained".equals(line.messageKey())));

        ProfitLossStatement.Side off = side(totals("905", "600", "120"), CURRENT.sales(), CURRENT.expenses(),
                CURRENT.outside());
        List<StatementLine> lines = ProfitLossStatement.lines(off, PREVIOUS);
        StatementLine unexplained = line(lines, "profitloss.line.unexplained");
        assertEquals(money("5"), unexplained.current());
        assertEquals(0, BigDecimal.ZERO.compareTo(unexplained.previous()));
        assertEquals(lines.indexOf(line(lines, "profitloss.net.sales")) - 1, lines.indexOf(unexplained),
                "it sits just above the subtotal it explains");
    }

    @Test
    void nothingAtAll() {
        ProfitLossStatement.Side empty = side(ProfitLossFigures.ZERO, SalesBreakdown.ZERO, List.of(),
                OutsideProfitFigures.ZERO);
        List<StatementLine> lines = ProfitLossStatement.lines(empty, empty);
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().filter(line -> !line.isHeading()).allMatch(line -> line.current().signum() == 0));
    }

    private static StatementLine line(List<StatementLine> lines, String key) {
        return lines.stream().filter(line -> key.equals(line.messageKey())).findFirst().orElseThrow();
    }
}
