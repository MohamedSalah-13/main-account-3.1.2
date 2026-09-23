package com.hamza.account.features.report.summary;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import com.hamza.account.features.report.monthly.DayFigures;
import com.hamza.account.features.report.monthly.MonthFigures;
import com.hamza.account.features.report.monthly.MonthlyTotalsReport;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.treasury.TreasuryBalanceSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The summary over a period, with the period before it beside the figures that are a period's.
 *
 * <p><b>The sales and the purchases are net</b> - {@link MonthFigures#net()}, the invoices less their
 * discounts less what came back net of its own - the profit and loss's net sales and the monthly totals'
 * figure. The summary showed the invoices before their discount with nothing returned taken off, so on
 * the same days it and the profit and loss said two different things. <b>The discounts are the sales'
 * own</b>: the old card added what was given away on sales to what was taken off purchases and returns,
 * a sum that is nobody's figure.</p>
 *
 * <p>A part the reader may not see ({@link SummaryCard}) is {@code null} and was never read.</p>
 *
 * @param previous the days the period is compared with - {@link ProfitLossPeriod#previous}'s rule: the same
 *                 days of the month before for a period starting on the 1st, the same number of days
 *                 straight before otherwise
 */
public record Summary(ProfitLossPeriod period, ProfitLossPeriod previous, LocalDate today, Set<SummaryCard> cards,
                      MonthFigures sales, MonthFigures previousSales, List<TrendPoint> trend,
                      MonthFigures purchases, MonthFigures previousPurchases,
                      CashFlow cash, CashFlow previousCash,
                      Receivables receivables, LowStock lowStock, List<TopSellingItem> topItems,
                      List<TreasuryBalanceSummary> treasuries) {

    public Summary {
        cards = Set.copyOf(cards);
        trend = trend == null ? List.of() : List.copyOf(trend);
        topItems = topItems == null ? List.of() : List.copyOf(topItems);
        treasuries = treasuries == null ? List.of() : List.copyOf(treasuries);
    }

    public boolean shows(SummaryCard card) {
        return cards.contains(card);
    }

    /** The change in net sales on the period before, in percent; empty when there is nothing to compare with. */
    public Optional<BigDecimal> salesChange() {
        if (sales == null || previousSales == null) {
            return Optional.empty();
        }
        return MonthlyTotalsReport.change(sales.net(), previousSales.net());
    }

    /** The days' figures inside a period, added up. */
    public static MonthFigures within(List<DayFigures> days, ProfitLossPeriod period) {
        MonthFigures total = MonthFigures.ZERO;
        for (DayFigures day : days) {
            if (period.contains(day.day())) {
                total = total.plus(day.figures());
            }
        }
        return total;
    }

    /** Every day of a period with its net - a day with no document as zero, so the line does not skip it. */
    public static List<TrendPoint> trendOf(List<DayFigures> days, ProfitLossPeriod period) {
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            LocalDate which = day;
            BigDecimal net = days.stream().filter(figures -> figures.day().equals(which))
                    .map(figures -> figures.figures().net()).reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new TrendPoint(day, net));
        }
        return points;
    }
}
