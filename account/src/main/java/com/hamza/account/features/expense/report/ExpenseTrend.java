package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.party.trend.TrendGranularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What was spent, drawn over time by year, month or week, with the same dates a year earlier beside it
 * (docs/expenses-plan.md §4, item 3).
 * <p>
 * It is the collections trend's arithmetic applied to one figure, and deliberately reuses its grouping
 * rather than repeating it: the periods come from {@link TrendGranularity}, so a week starts on the same
 * Saturday on the statement, the collections chart and this one. Every period in the range is a point,
 * including the empty ones - a line that skips a zero lies about the zero. And the comparison is the same
 * calendar dates a year back, filed into this year's periods.
 */
public record ExpenseTrend(Filter filter, List<Point> points, BigDecimal total, BigDecimal previousTotal) {

    /** The most periods one chart draws - the collections chart's ceiling, for the same unreadable axis. */
    public static final int MAX_PERIODS = 120;

    /** Why a range cannot be charted. */
    public enum Problem {
        NONE,
        NO_PERIOD,
        REVERSED,
        TOO_MANY_PERIODS
    }

    /**
     * What one chart is asked for. The scope must carry both dates: a trend of "everything ever" has no
     * first period to start from.
     */
    public record Filter(ExpenseFilter scope, TrendGranularity granularity, boolean compareWithPreviousYear) {

        public Filter {
            if (scope == null || granularity == null) {
                throw new IllegalArgumentException("a trend needs a scope and a grouping");
            }
            Problem problem = problem(granularity, scope.from(), scope.to());
            if (problem != Problem.NONE) {
                throw new IllegalArgumentException("cannot chart " + scope.from() + " to " + scope.to()
                        + " by " + granularity + ": " + problem);
            }
            compareWithPreviousYear = compareWithPreviousYear && granularity.comparesWithPreviousYear();
        }

        /** Whether this range can be charted by this grouping - asked by the screen first, in words. */
        public static Problem problem(TrendGranularity granularity, LocalDate from, LocalDate to) {
            if (from == null || to == null) {
                return Problem.NO_PERIOD;
            }
            if (from.isAfter(to)) {
                return Problem.REVERSED;
            }
            if (granularity.periods(from, to) > MAX_PERIODS) {
                return Problem.TOO_MANY_PERIODS;
            }
            return Problem.NONE;
        }

        /** The same conditions a year back. */
        public ExpenseFilter previousScope() {
            return scope.withPeriod(scope.from().minusYears(1), scope.to().minusYears(1));
        }
    }

    /**
     * One period.
     *
     * @param end           its last day charted - the period's own, or the range's when it stops inside it
     * @param previousTotal the same dates a year earlier; zero without a comparison
     */
    public record Point(LocalDate start, LocalDate end, String label, BigDecimal total, BigDecimal previousTotal) {
    }

    public ExpenseTrend {
        points = List.copyOf(points);
    }

    public static ExpenseTrend build(Filter filter, List<ExpenseReportRows.Day> current,
                                     List<ExpenseReportRows.Day> previous) {
        TrendGranularity granularity = filter.granularity();
        LocalDate from = filter.scope().from();
        LocalDate to = filter.scope().to();
        Map<LocalDate, BigDecimal[]> periods = new LinkedHashMap<>();
        for (LocalDate start = granularity.start(from); !start.isAfter(to); start = granularity.next(start)) {
            periods.put(start, new BigDecimal[]{ExpensePeriods.NONE, ExpensePeriods.NONE});
        }
        for (ExpenseReportRows.Day day : current) {
            if (!day.day().isBefore(from) && !day.day().isAfter(to)) {
                BigDecimal[] totals = periods.get(granularity.start(day.day()));
                totals[0] = totals[0].add(day.total());
            }
        }
        if (filter.compareWithPreviousYear()) {
            for (ExpenseReportRows.Day day : previous) {
                LocalDate sameDateThisYear = day.day().plusYears(1);
                if (!sameDateThisYear.isBefore(from) && !sameDateThisYear.isAfter(to)) {
                    BigDecimal[] totals = periods.get(granularity.start(sameDateThisYear));
                    totals[1] = totals[1].add(day.total());
                }
            }
        }

        List<Point> points = new ArrayList<>(periods.size());
        BigDecimal total = ExpensePeriods.NONE;
        BigDecimal previousTotal = ExpensePeriods.NONE;
        for (Map.Entry<LocalDate, BigDecimal[]> entry : periods.entrySet()) {
            LocalDate start = entry.getKey();
            LocalDate lastDay = granularity.next(start).minusDays(1);
            BigDecimal[] totals = entry.getValue();
            points.add(new Point(start, lastDay.isAfter(to) ? to : lastDay, granularity.label(start),
                    totals[0], totals[1]));
            total = total.add(totals[0]);
            previousTotal = previousTotal.add(totals[1]);
        }
        return new ExpenseTrend(filter, points, total, previousTotal);
    }

    /** Against the same dates a year back; empty without a comparison or with nothing then. */
    public Optional<BigDecimal> change() {
        return filter.compareWithPreviousYear() ? ExpensePeriods.change(total, previousTotal) : Optional.empty();
    }

    /** What an average period came to, over every period drawn - the empty ones count. */
    public BigDecimal averagePerPeriod() {
        return points.isEmpty() ? ExpensePeriods.NONE
                : total.divide(BigDecimal.valueOf(points.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isEmpty() {
        return total.signum() == 0 && previousTotal.signum() == 0;
    }

    /** The list's filter for one point: the scope over the point's own days. */
    public ExpenseFilter listFilter(Point point) {
        return ExpensePeriods.within(filter.scope(), point.start(), point.end());
    }
}
