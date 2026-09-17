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
 * Expenses against net sales, month by month (docs/expenses-plan.md §4, item 5).
 * <p>
 * <b>The two sides are not filtered alike, and that is the question.</b> The expenses are the scope's -
 * a heading, a till, a person narrow them as they narrow the list. The sales are the business's net sales
 * over the same days, whatever else the scope says: "what share of what we sold went on electricity" is a
 * question about all the sales, and a sale has no heading to narrow by.
 * <p>
 * <b>Net sales are {@code document_profit}'s {@code net_revenue}</b> - a sale net of its discounts, a
 * return signed against it - read by {@link ExpenseReportQuery#NET_SALES_DAYS_SQL}. A month whose returns
 * outweigh its sales has no meaningful ratio, and neither has a month that sold nothing: both are absent.
 */
public record ExpenseSalesRatio(ExpenseFilter scope, List<Line> lines, BigDecimal expenses, BigDecimal netSales) {

    /** The longest range the ratio lists, by the month: ten years. */
    public static final int MAX_MONTHS = 120;

    public record Line(LocalDate start, LocalDate end, String label, BigDecimal expenses, BigDecimal netSales) {

        public Optional<BigDecimal> ratio() {
            return ExpenseSalesRatio.ratio(expenses, netSales);
        }
    }

    public ExpenseSalesRatio {
        lines = List.copyOf(lines);
    }

    /** Whether the scope's dates can be listed by the month; the trend's problems, measured in months. */
    public static ExpenseTrend.Problem problem(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return ExpenseTrend.Problem.NO_PERIOD;
        }
        if (from.isAfter(to)) {
            return ExpenseTrend.Problem.REVERSED;
        }
        return TrendGranularity.MONTH.periods(from, to) > MAX_MONTHS
                ? ExpenseTrend.Problem.TOO_MANY_PERIODS : ExpenseTrend.Problem.NONE;
    }

    public static ExpenseSalesRatio build(ExpenseFilter scope, List<ExpenseReportRows.Day> expenseDays,
                                          List<ExpenseReportRows.Day> salesDays) {
        if (problem(scope.from(), scope.to()) != ExpenseTrend.Problem.NONE) {
            throw new IllegalArgumentException("cannot list " + scope.from() + " to " + scope.to() + " by the month");
        }
        TrendGranularity months = TrendGranularity.MONTH;
        Map<LocalDate, BigDecimal[]> periods = new LinkedHashMap<>();
        for (LocalDate start = months.start(scope.from()); !start.isAfter(scope.to()); start = months.next(start)) {
            periods.put(start, new BigDecimal[]{ExpensePeriods.NONE, ExpensePeriods.NONE});
        }
        file(periods, expenseDays, 0, scope);
        file(periods, salesDays, 1, scope);

        List<Line> lines = new ArrayList<>(periods.size());
        BigDecimal expenses = ExpensePeriods.NONE;
        BigDecimal netSales = ExpensePeriods.NONE;
        for (Map.Entry<LocalDate, BigDecimal[]> entry : periods.entrySet()) {
            LocalDate start = entry.getKey();
            LocalDate lastDay = months.next(start).minusDays(1);
            BigDecimal[] totals = entry.getValue();
            lines.add(new Line(start.isBefore(scope.from()) ? scope.from() : start,
                    lastDay.isAfter(scope.to()) ? scope.to() : lastDay,
                    months.label(start), totals[0], totals[1]));
            expenses = expenses.add(totals[0]);
            netSales = netSales.add(totals[1]);
        }
        return new ExpenseSalesRatio(scope, lines, expenses, netSales);
    }

    public Optional<BigDecimal> ratio() {
        return ratio(expenses, netSales);
    }

    public boolean isEmpty() {
        return expenses.signum() == 0 && netSales.signum() == 0;
    }

    /** The list's filter for one month: the scope over that month's days inside it. */
    public ExpenseFilter listFilter(Line line) {
        return scope.withPeriod(line.start(), line.end());
    }

    static Optional<BigDecimal> ratio(BigDecimal expenses, BigDecimal netSales) {
        return netSales.signum() > 0 ? ExpensePeriods.percent(expenses, netSales) : Optional.empty();
    }

    private static void file(Map<LocalDate, BigDecimal[]> periods, List<ExpenseReportRows.Day> days, int side,
                             ExpenseFilter scope) {
        for (ExpenseReportRows.Day day : days) {
            if (!day.day().isBefore(scope.from()) && !day.day().isAfter(scope.to())) {
                BigDecimal[] totals = periods.get(TrendGranularity.MONTH.start(day.day()));
                totals[side] = totals[side].add(day.total());
            }
        }
    }
}
