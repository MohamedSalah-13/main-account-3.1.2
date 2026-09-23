package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The profit and loss statement as a page: revenue, cost, the gross profit, expenses by heading, the net
 * profit, and under it what the owner decided is shown and not counted.
 *
 * <p><b>The subtotals and the results are the statement's own figures</b> - {@link ProfitLossFigures},
 * the days {@code ProfitLossDao} answers, summed - never the lines above them added up. The lines explain
 * those figures; they do not replace them. Where they do not add up, a line says by how much, so a section
 * still adds up on the page and the difference is in front of the reader rather than hidden in a total.</p>
 */
public final class ProfitLossStatement {

    private ProfitLossStatement() {
    }

    /** One side of the comparison: the period's figures and what explains them. */
    public record Side(ProfitLossFigures totals, SalesBreakdown sales, List<ExpenseHeadingTotal> expenses,
                       OutsideProfitFigures outside) {
        public Side {
            expenses = List.copyOf(expenses);
        }
    }

    public static List<StatementLine> lines(Side current, Side previous) {
        List<StatementLine> lines = new ArrayList<>();

        lines.add(StatementLine.heading("profitloss.section.revenue"));
        List<StatementLine> revenue = List.of(
                item("profitloss.line.gross.sales", current, previous, side -> side.sales().grossSales()),
                item("profitloss.line.invoice.discounts", current, previous,
                        side -> side.sales().invoiceDiscounts().negate()),
                item("profitloss.line.returns", current, previous, side -> side.sales().returns().negate()));
        section(lines, revenue, StatementLine.subtotal("profitloss.net.sales",
                current.totals().netSales(), previous.totals().netSales()));

        lines.add(StatementLine.heading("profitloss.section.cost"));
        List<StatementLine> cost = List.of(
                item("profitloss.line.cost.sold", current, previous, side -> side.sales().costOfSold().negate()),
                item("profitloss.line.cost.returned", current, previous, side -> side.sales().costOfReturned()));
        section(lines, cost, StatementLine.subtotal("profitloss.cost.sales",
                current.totals().costOfSales().negate(), previous.totals().costOfSales().negate()));
        lines.add(StatementLine.result("profitloss.gross.profit",
                current.totals().grossProfit(), previous.totals().grossProfit()));

        lines.add(StatementLine.heading("profitloss.section.expenses"));
        section(lines, expenseLines(current.expenses(), previous.expenses()),
                StatementLine.subtotal("profitloss.line.expenses.total",
                        current.totals().expenses().negate(), previous.totals().expenses().negate()));
        lines.add(StatementLine.result("profitloss.net.profit",
                current.totals().netProfit(), previous.totals().netProfit()));

        lines.add(StatementLine.heading("profitloss.section.outside"));
        lines.add(item("profitloss.line.stock.shortage", current, previous,
                side -> side.outside().stockShortage().negate()));
        lines.add(item("profitloss.line.stock.surplus", current, previous, side -> side.outside().stockSurplus()));
        lines.add(item("profitloss.line.till.shortage", current, previous,
                side -> side.outside().tillShortage().negate()));
        lines.add(item("profitloss.line.till.surplus", current, previous, side -> side.outside().tillSurplus()));
        lines.add(StatementLine.subtotal("profitloss.line.outside.net",
                current.outside().net(), previous.outside().net()));
        return List.copyOf(lines);
    }

    /** The lines, the difference they leave against the section's own figure if any, and the figure. */
    private static void section(List<StatementLine> lines, List<StatementLine> items, StatementLine subtotal) {
        lines.addAll(items);
        BigDecimal current = subtotal.current();
        BigDecimal previous = subtotal.previous();
        for (StatementLine item : items) {
            current = current.subtract(item.current());
            previous = previous.subtract(item.previous());
        }
        if (current.signum() != 0 || previous.signum() != 0) {
            lines.add(StatementLine.item("profitloss.line.unexplained", current, previous));
        }
        lines.add(subtotal);
    }

    /**
     * A line per main heading either period spent under, the larger this period first. A heading only
     * the compared period used still has a line: an expense that stopped is part of the comparison.
     */
    private static List<StatementLine> expenseLines(List<ExpenseHeadingTotal> current,
                                                    List<ExpenseHeadingTotal> previous) {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, BigDecimal> now = new LinkedHashMap<>();
        Map<Integer, BigDecimal> before = new LinkedHashMap<>();
        for (ExpenseHeadingTotal heading : current) {
            names.put(heading.headingId(), heading.name());
            now.merge(heading.headingId(), heading.amount(), BigDecimal::add);
        }
        for (ExpenseHeadingTotal heading : previous) {
            names.putIfAbsent(heading.headingId(), heading.name());
            before.merge(heading.headingId(), heading.amount(), BigDecimal::add);
        }
        return names.keySet().stream()
                .sorted(Comparator.<Integer, BigDecimal>comparing(id -> now.getOrDefault(id, BigDecimal.ZERO))
                        .thenComparing(id -> before.getOrDefault(id, BigDecimal.ZERO))
                        .reversed()
                        .thenComparing(names::get))
                .map(id -> StatementLine.named(names.get(id), now.getOrDefault(id, BigDecimal.ZERO).negate(),
                        before.getOrDefault(id, BigDecimal.ZERO).negate()))
                .toList();
    }

    private static StatementLine item(String key, Side current, Side previous,
                                      Function<Side, BigDecimal> amount) {
        return StatementLine.item(key, amount.apply(current), amount.apply(previous));
    }
}
