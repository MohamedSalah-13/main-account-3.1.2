package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The year's figures above the table, summed from its own rows.
 *
 * <p>Summed from the rows rather than asked of the database again, so the cards and the footer cannot
 * describe a different set of months from the table under them - the rule the collections trend and the
 * balances footer follow for the same reason.</p>
 *
 * <p>The best and the worst month are judged by net profit, among the months that traded at all: a month
 * with nothing in it is not the worst month, it is a month with nothing in it. With fewer than two such
 * months there is nothing to compare, and "the worst month" is absent rather than the best one twice.</p>
 */
public record YearlySummary(ProfitLossFigures current, ProfitLossFigures previous, MonthBreakdown breakdown,
                            Optional<YearlyReportRow> best, Optional<YearlyReportRow> worst,
                            int activeMonths, BigDecimal unexplainedSales) {

    static YearlySummary of(List<YearlyReportRow> rows) {
        ProfitLossFigures current = ProfitLossFigures.ZERO;
        ProfitLossFigures previous = ProfitLossFigures.ZERO;
        MonthBreakdown breakdown = MonthBreakdown.empty(1);
        BigDecimal unexplained = BigDecimal.ZERO;
        for (YearlyReportRow row : rows) {
            current = current.plus(row.current());
            previous = previous.plus(row.previous());
            breakdown = breakdown.plus(row.breakdown());
            unexplained = unexplained.add(row.unexplainedSales());
        }
        List<YearlyReportRow> active = rows.stream().filter(YearlyReportRow::hasActivity).toList();
        Comparator<YearlyReportRow> byProfit = Comparator.comparing(YearlyReportRow::netProfit);
        Optional<YearlyReportRow> best = active.stream().max(byProfit);
        Optional<YearlyReportRow> worst = active.size() < 2 ? Optional.empty() : active.stream().min(byProfit);
        return new YearlySummary(current, previous, breakdown, best, worst, active.size(), unexplained);
    }

    public Optional<BigDecimal> netSalesChange() {
        return ProfitLossFigures.change(current.netSales(), previous.netSales());
    }

    public Optional<BigDecimal> netProfitChange() {
        return ProfitLossFigures.change(current.netProfit(), previous.netProfit());
    }

    /** Whether the year before traded at all over the same dates - a comparison with nothing is not one. */
    public boolean hasPrevious() {
        return previous.hasActivity();
    }
}
