package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the money went, by heading: each heading's count, total, share of everything, and the change
 * against the period of equal length just before (docs/expenses-plan.md §4, item 1).
 * <p>
 * <b>The comparison is the list's.</b> "The period before" is {@link ExpenseFilter#previousPeriod()} - the
 * same number of days immediately before - so the change a heading shows here and the change the list's
 * card shows for the same filter are one computation. Without a whole period (an open end) there is no
 * comparison, and the column is absent rather than zero.
 *
 * @param scope         the filter the report was read over
 * @param previousTotal what the previous period came to, or {@code null} when there is none to compare
 */
public record ExpenseByHeadingReport(ExpenseFilter scope, List<Line> lines, int count, BigDecimal total,
                                     BigDecimal previousTotal) {

    /**
     * One line.
     *
     * @param previousTotal the heading's previous period, or {@code null} without a comparison
     */
    public record Line(ExpenseHeadingLineKind kind, int headingId, String name, int count, BigDecimal total,
                       BigDecimal previousTotal, BigDecimal sharePercent, BigDecimal changePercent) {

        public Optional<BigDecimal> share() {
            return Optional.ofNullable(sharePercent);
        }

        public Optional<BigDecimal> change() {
            return Optional.ofNullable(changePercent);
        }
    }

    public ExpenseByHeadingReport {
        lines = List.copyOf(lines);
    }

    /**
     * Files each heading's own totals into the tree.
     *
     * @param previous the previous period's totals, or {@code null} when the scope has no whole period
     */
    public static ExpenseByHeadingReport build(ExpenseFilter scope, List<ExpenseHeading> headings,
                                               List<ExpenseReportRows.HeadingTotal> current,
                                               List<ExpenseReportRows.HeadingTotal> previous) {
        boolean compared = previous != null;
        Map<Integer, ExpenseHeadingLines.Amounts> amounts = new HashMap<>();
        for (ExpenseReportRows.HeadingTotal row : current) {
            ExpenseHeadingLines.Amounts entry = amounts.computeIfAbsent(row.headingId(),
                    id -> new ExpenseHeadingLines.Amounts());
            entry.count += row.count();
            entry.total = entry.total.add(row.total());
        }
        if (compared) {
            for (ExpenseReportRows.HeadingTotal row : previous) {
                ExpenseHeadingLines.Amounts entry = amounts.computeIfAbsent(row.headingId(),
                        id -> new ExpenseHeadingLines.Amounts());
                entry.previous = entry.previous.add(row.total());
            }
        }

        List<ExpenseHeadingLines.Line> laidOut = ExpenseHeadingLines.of(headings, amounts);
        ExpenseHeadingLines.Amounts grand = ExpenseHeadingLines.grandTotal(laidOut);
        List<Line> lines = new ArrayList<>(laidOut.size());
        for (ExpenseHeadingLines.Line line : laidOut) {
            ExpenseHeadingLines.Amounts figures = line.amounts();
            lines.add(new Line(line.kind(), line.id(), line.name(), figures.count, figures.total,
                    compared ? figures.previous : null,
                    ExpensePeriods.percent(figures.total, grand.total).orElse(null),
                    compared ? ExpensePeriods.change(figures.total, figures.previous).orElse(null) : null));
        }
        return new ExpenseByHeadingReport(scope, lines, grand.count, grand.total, compared ? grand.previous : null);
    }

    public boolean compared() {
        return previousTotal != null;
    }

    public Optional<BigDecimal> change() {
        return compared() ? ExpensePeriods.change(total, previousTotal) : Optional.empty();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** The list's filter for one line, or empty for a direct line - see {@link ExpenseHeadingLineKind#DIRECT}. */
    public Optional<ExpenseFilter> listFilter(Line line) {
        return line.kind().opensList() ? Optional.of(scope.withHeading(line.headingId())) : Optional.empty();
    }
}
