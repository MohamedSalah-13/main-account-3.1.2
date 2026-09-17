package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The expenses totalled by one dimension - the till, who entered them, the shift, the payee or the month
 * (docs/expenses-plan.md §4, item 4).
 *
 * @param scope the filter it was read over
 */
public record ExpenseDimensionReport(ExpenseFilter scope, ExpenseDimension dimension, List<Line> lines,
                                     int count, BigDecimal total) {

    /**
     * One value.
     *
     * @param key   what the list is narrowed by, or {@code null} for the expenses carrying no value
     * @param label what the line is called, or {@code null} for that same line - the screen names it
     */
    public record Line(String key, String label, int count, BigDecimal total, BigDecimal sharePercent) {

        public Optional<BigDecimal> share() {
            return Optional.ofNullable(sharePercent);
        }
    }

    public ExpenseDimensionReport {
        lines = List.copyOf(lines);
    }

    public static ExpenseDimensionReport build(ExpenseFilter scope, ExpenseDimension dimension,
                                               List<ExpenseReportRows.DimensionTotal> rows) {
        int count = 0;
        BigDecimal total = ExpensePeriods.NONE;
        for (ExpenseReportRows.DimensionTotal row : rows) {
            count += row.count();
            total = total.add(row.total());
        }
        List<Line> lines = new ArrayList<>(rows.size());
        for (ExpenseReportRows.DimensionTotal row : rows) {
            lines.add(new Line(row.key(), dimension.label(row.key(), row.label()), row.count(), row.total(),
                    ExpensePeriods.percent(row.total(), total).orElse(null)));
        }
        lines.sort(order(dimension));
        return new ExpenseDimensionReport(scope, dimension, lines, count, total);
    }

    /**
     * The line with no value goes last whatever the order: it is the remainder, not a till or a payee that
     * happens to be large.
     */
    private static Comparator<Line> order(ExpenseDimension dimension) {
        Comparator<Line> noValueLast = Comparator.comparing(line -> line.key() == null);
        if (dimension.sortsByKey()) {
            return noValueLast.thenComparing(line -> line.key() == null ? 0L : Long.parseLong(line.key()));
        }
        return noValueLast
                .thenComparing(Line::total, Comparator.reverseOrder())
                .thenComparing(line -> line.label() == null ? "" : line.label());
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public Optional<ExpenseFilter> listFilter(Line line) {
        return dimension.narrow(scope, line.key());
    }
}
