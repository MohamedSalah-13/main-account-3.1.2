package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One year, heading by month: twelve columns and a total, a line per heading laid out as the report by
 * heading lays them out, and a line of month totals under it (docs/expenses-plan.md §4, item 2).
 * <p>
 * <b>The year replaces the scope's period and keeps everything else.</b> A matrix of the months of a
 * period that starts on the 17th would show a first column that is not a month; so the scope's heading,
 * till, person, amounts and text still narrow it, and its dates do not.
 *
 * @param scope the filter it was read over, with the year's dates already on it
 */
public record ExpenseYearMatrix(ExpenseFilter scope, int year, List<Line> lines, Line totals) {

    /** One line: twelve months, January first, and what they come to. */
    public record Line(ExpenseHeadingLineKind kind, int headingId, String name, List<BigDecimal> months,
                       BigDecimal total) {

        public Line {
            months = List.copyOf(months);
            if (months.size() != 12) {
                throw new IllegalArgumentException("a year has twelve months: " + months.size());
            }
        }

        /** One month, {@code 1} for January. */
        public BigDecimal month(int month) {
            return months.get(month - 1);
        }
    }

    public ExpenseYearMatrix {
        lines = List.copyOf(lines);
    }

    /** The scope with the year's own dates. */
    public static ExpenseFilter yearScope(ExpenseFilter scope, int year) {
        return scope.withPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /**
     * Files each heading's days into months.
     *
     * @param days what each heading came to per day; a day outside the year is not filed anywhere
     */
    public static ExpenseYearMatrix build(ExpenseFilter scope, int year, List<ExpenseHeading> headings,
                                          List<ExpenseReportRows.HeadingDay> days) {
        ExpenseFilter yearScope = yearScope(scope, year);
        Map<Integer, ExpenseHeadingLines.Amounts> amounts = new HashMap<>();
        for (ExpenseReportRows.HeadingDay day : days) {
            if (day.day().getYear() != year) {
                continue;
            }
            ExpenseHeadingLines.Amounts entry = amounts.computeIfAbsent(day.headingId(),
                    id -> new ExpenseHeadingLines.Amounts());
            int month = day.day().getMonthValue() - 1;
            entry.months[month] = entry.months[month].add(day.total());
            entry.total = entry.total.add(day.total());
            // Days, not expenses: the matrix shows amounts only, and a count here would be a count of days.
            entry.count++;
        }

        List<ExpenseHeadingLines.Line> laidOut = ExpenseHeadingLines.of(headings, amounts);
        List<Line> lines = new ArrayList<>(laidOut.size());
        for (ExpenseHeadingLines.Line line : laidOut) {
            lines.add(new Line(line.kind(), line.id(), line.name(), Arrays.asList(line.amounts().months),
                    line.amounts().total));
        }
        ExpenseHeadingLines.Amounts grand = ExpenseHeadingLines.grandTotal(laidOut);
        return new ExpenseYearMatrix(yearScope, year, lines,
                new Line(ExpenseHeadingLineKind.MAIN, 0, "", Arrays.asList(grand.months), grand.total));
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * The list's filter for one cell: the line's heading - none on the totals line - over one month, or
     * over the year for {@code month == 0}. Empty for a direct line.
     */
    public Optional<ExpenseFilter> listFilter(Line line, int month) {
        if (!line.kind().opensList()) {
            return Optional.empty();
        }
        if (month < 0 || month > 12) {
            throw new IllegalArgumentException("month must be 0 for the year or 1-12: " + month);
        }
        ExpenseFilter narrowed = line == totals ? scope : scope.withHeading(line.headingId());
        if (month == 0) {
            return Optional.of(narrowed);
        }
        LocalDate first = LocalDate.of(year, month, 1);
        return Optional.of(narrowed.withPeriod(first, first.plusMonths(1).minusDays(1)));
    }
}
