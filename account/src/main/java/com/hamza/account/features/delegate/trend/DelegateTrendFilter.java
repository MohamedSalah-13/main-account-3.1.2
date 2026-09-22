package com.hamza.account.features.delegate.trend;

import com.hamza.account.features.party.trend.PartyTrendFilter;
import com.hamza.account.features.party.trend.TrendGranularity;

import java.time.LocalDate;

/**
 * What one delegate's trend is asked for.
 * <p>
 * The periods and their ceiling are the party trend's - {@link TrendGranularity} and
 * {@link PartyTrendFilter#problem} - so a month, a week starting on Saturday and "too many points to
 * read" mean one thing on both charts.
 *
 * @param delegateId              the delegate, an employee id
 * @param granularity             how days are grouped into periods
 * @param from                    the first day charted
 * @param to                      the last day charted, inclusive
 * @param compareWithPreviousYear whether the same dates a year back are charted beside these. Always
 *                                false by year, where it would repeat the point beside
 */
public record DelegateTrendFilter(int delegateId, TrendGranularity granularity, LocalDate from,
                                  LocalDate to, boolean compareWithPreviousYear) {

    public DelegateTrendFilter {
        if (delegateId <= 0) {
            throw new IllegalArgumentException("a trend belongs to one delegate: " + delegateId);
        }
        if (granularity == null || from == null || to == null) {
            throw new IllegalArgumentException("a trend needs a grouping and two dates");
        }
        PartyTrendFilter.Problem problem = PartyTrendFilter.problem(granularity, from, to);
        if (problem != PartyTrendFilter.Problem.NONE) {
            throw new IllegalArgumentException("cannot chart " + from + " to " + to + " by " + granularity
                    + ": " + problem);
        }
        compareWithPreviousYear = compareWithPreviousYear && granularity.comparesWithPreviousYear();
    }

    /** The first day of the comparison: the same date, a year back. */
    public LocalDate previousFrom() {
        return from.minusYears(1);
    }

    /** The last day of the comparison. */
    public LocalDate previousTo() {
        return to.minusYears(1);
    }
}
