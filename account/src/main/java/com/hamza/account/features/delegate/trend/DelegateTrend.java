package com.hamza.account.features.delegate.trend;

import com.hamza.account.features.party.trend.TrendGranularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One delegate's trend: his periods in order, and their totals.
 * <p>
 * The two rules of the party trend, for the same reasons: <b>every period in the range is a point</b>,
 * a quiet month included, since a line that skips a zero lies about it; and <b>the comparison is the
 * same calendar dates a year back, filed into this year's periods</b> - exact by the month, and by the
 * week a comparison of dates rather than of week numbers nobody keeps.
 */
public record DelegateTrend(DelegateTrendFilter filter, List<DelegateTrendPoint> points,
                            DelegateTrendSummary summary) {

    /** Zero with the two places money carries, so an empty period prints as 0.00. */
    static final BigDecimal NONE = new BigDecimal("0.00");

    public DelegateTrend {
        points = List.copyOf(points);
    }

    /**
     * Files each day into its period.
     *
     * @param current  the days of the range itself
     * @param previous the same range a year earlier; ignored without a comparison
     */
    public static DelegateTrend build(DelegateTrendFilter filter, List<DelegateTrendDay> current,
                                      List<DelegateTrendDay> previous) {
        TrendGranularity granularity = filter.granularity();
        Map<LocalDate, Totals> periods = new LinkedHashMap<>();
        for (LocalDate start = granularity.start(filter.from()); !start.isAfter(filter.to());
             start = granularity.next(start)) {
            periods.put(start, new Totals());
        }
        for (DelegateTrendDay day : current) {
            if (inRange(filter, day.day())) {
                Totals totals = periods.get(granularity.start(day.day()));
                totals.sales = totals.sales.add(day.sales());
                totals.returns = totals.returns.add(day.salesReturns());
                totals.collected = totals.collected.add(day.collected());
            }
        }
        if (filter.compareWithPreviousYear()) {
            for (DelegateTrendDay day : previous) {
                LocalDate sameDateThisYear = day.day().plusYears(1);
                if (inRange(filter, sameDateThisYear)) {
                    Totals totals = periods.get(granularity.start(sameDateThisYear));
                    totals.previousNet = totals.previousNet.add(day.sales()).subtract(day.salesReturns());
                    totals.previousCollected = totals.previousCollected.add(day.collected());
                }
            }
        }
        List<DelegateTrendPoint> points = new ArrayList<>(periods.size());
        for (Map.Entry<LocalDate, Totals> entry : periods.entrySet()) {
            LocalDate start = entry.getKey();
            LocalDate lastDay = granularity.next(start).minusDays(1);
            Totals totals = entry.getValue();
            points.add(new DelegateTrendPoint(start, lastDay.isAfter(filter.to()) ? filter.to() : lastDay,
                    granularity.label(start), totals.sales, totals.returns, totals.collected,
                    totals.previousNet, totals.previousCollected));
        }
        return new DelegateTrend(filter, points, DelegateTrendSummary.of(points, filter.compareWithPreviousYear()));
    }

    /** Whether nothing at all moved in the range - the chart then says so instead of drawing zeros. */
    public boolean isEmpty() {
        return points.stream().allMatch(point -> point.sales().signum() == 0
                && point.salesReturns().signum() == 0 && point.collected().signum() == 0
                && point.previousNetSales().signum() == 0 && point.previousCollected().signum() == 0);
    }

    /** A day the query should not have returned is not filed anywhere, rather than misfiled. */
    private static boolean inRange(DelegateTrendFilter filter, LocalDate day) {
        return !day.isBefore(filter.from()) && !day.isAfter(filter.to());
    }

    private static final class Totals {
        private BigDecimal sales = NONE;
        private BigDecimal returns = NONE;
        private BigDecimal collected = NONE;
        private BigDecimal previousNet = NONE;
        private BigDecimal previousCollected = NONE;
    }
}
