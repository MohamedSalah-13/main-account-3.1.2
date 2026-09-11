package com.hamza.account.features.party.trend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A charted trend: the periods in order, and their totals.
 * <p>
 * <b>Every period in the range is a point, including the ones where nothing moved.</b> A chart
 * built only from the periods that have rows joins the month before a quiet one straight to the
 * month after, and a line that skips a zero is a line that lies about the zero.
 * <p>
 * <b>The comparison is the same calendar dates a year back, filed into this year's periods.</b>
 * By the month that is exact. By the week it is the same dates, which fall on other weekdays a
 * year earlier, so a day at the edge of a week can land in the week beside it - a comparison of
 * dates, not of "the 36th week", which is a number no customer keeps.
 */
public record PartyTrend(PartyTrendFilter filter, List<PartyTrendPoint> points, PartyTrendSummary summary) {

    /** Zero, with the two places money carries - so an empty period prints as 0.00. */
    static final BigDecimal NONE = new BigDecimal("0.00");

    public PartyTrend {
        points = List.copyOf(points);
    }

    /**
     * Files each day into its period.
     *
     * @param current  the days of the range itself
     * @param previous the days of the same range a year earlier; ignored without a comparison
     */
    public static PartyTrend build(PartyTrendFilter filter, List<PartyTrendDay> current,
                                   List<PartyTrendDay> previous) {
        TrendGranularity granularity = filter.granularity();
        Map<LocalDate, Totals> periods = new LinkedHashMap<>();
        for (LocalDate start = granularity.start(filter.from()); !start.isAfter(filter.to());
             start = granularity.next(start)) {
            periods.put(start, new Totals());
        }

        for (PartyTrendDay day : current) {
            if (inRange(filter, day.day())) {
                Totals totals = periods.get(granularity.start(day.day()));
                totals.debit = totals.debit.add(day.debit());
                totals.credit = totals.credit.add(day.credit());
            }
        }
        if (filter.compareWithPreviousYear()) {
            for (PartyTrendDay day : previous) {
                LocalDate sameDateThisYear = day.day().plusYears(1);
                if (inRange(filter, sameDateThisYear)) {
                    Totals totals = periods.get(granularity.start(sameDateThisYear));
                    totals.previousDebit = totals.previousDebit.add(day.debit());
                    totals.previousCredit = totals.previousCredit.add(day.credit());
                }
            }
        }

        List<PartyTrendPoint> points = new ArrayList<>(periods.size());
        for (Map.Entry<LocalDate, Totals> entry : periods.entrySet()) {
            LocalDate start = entry.getKey();
            LocalDate lastDay = granularity.next(start).minusDays(1);
            Totals totals = entry.getValue();
            points.add(new PartyTrendPoint(start,
                    lastDay.isAfter(filter.to()) ? filter.to() : lastDay,
                    granularity.label(start),
                    totals.debit, totals.credit, totals.previousDebit, totals.previousCredit));
        }
        return new PartyTrend(filter, points,
                PartyTrendSummary.of(points, filter.compareWithPreviousYear()));
    }

    /** Whether nothing at all moved in the range - the chart then says so instead of drawing zeros. */
    public boolean isEmpty() {
        return summary.debit().signum() == 0 && summary.credit().signum() == 0
                && summary.previousDebit().signum() == 0 && summary.previousCredit().signum() == 0;
    }

    /** A day the query should not have returned is not filed anywhere, rather than misfiled. */
    private static boolean inRange(PartyTrendFilter filter, LocalDate day) {
        return !day.isBefore(filter.from()) && !day.isAfter(filter.to());
    }

    private static final class Totals {
        private BigDecimal debit = NONE;
        private BigDecimal credit = NONE;
        private BigDecimal previousDebit = NONE;
        private BigDecimal previousCredit = NONE;
    }
}
