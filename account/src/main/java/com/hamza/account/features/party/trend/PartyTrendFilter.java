package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;

import java.time.LocalDate;

/**
 * What one trend chart is asked for.
 * <p>
 * The checks are in the constructor, as on every filter in {@code features/party}: a filter that
 * exists is one the query can answer. A person can still pick a start after an end, or two dates
 * three years apart by the week - so {@link #problem} is how a screen asks first, and says so in
 * words, rather than meeting the constructor's refusal as a reference code.
 *
 * @param kind                    customers or suppliers - chooses the ledger view and the permission
 * @param granularity             how days are grouped into periods
 * @param from                    first day charted
 * @param to                      last day charted, inclusive
 * @param partyId                 one party, or {@code null} for all of them
 * @param compareWithPreviousYear whether the same dates a year back are charted beside these.
 *                                Always false by year, where it would repeat the point beside
 */
public record PartyTrendFilter(PartyKind kind, TrendGranularity granularity, LocalDate from,
                               LocalDate to, Integer partyId, boolean compareWithPreviousYear) {

    /**
     * The most periods one chart draws. Ten years by the month; a little over two years by the
     * week. Past that the points run into each other and the axis stops being readable.
     */
    public static final int MAX_PERIODS = 120;

    /** Why a range cannot be charted, in an order a screen can translate. */
    public enum Problem {
        NONE,
        REVERSED,
        TOO_MANY_PERIODS
    }

    public PartyTrendFilter {
        if (kind == null) {
            throw new IllegalArgumentException("a trend belongs to customers or to suppliers");
        }
        if (granularity == null || from == null || to == null) {
            throw new IllegalArgumentException("a trend needs a grouping and two dates");
        }
        if (partyId != null && partyId <= 0) {
            throw new IllegalArgumentException("partyId must identify a party: " + partyId);
        }
        Problem problem = problem(granularity, from, to);
        if (problem != Problem.NONE) {
            throw new IllegalArgumentException("cannot chart " + from + " to " + to
                    + " by " + granularity + ": " + problem);
        }
        compareWithPreviousYear = compareWithPreviousYear && granularity.comparesWithPreviousYear();
    }

    /** Whether this range can be charted by this grouping, and if not, why. */
    public static Problem problem(TrendGranularity granularity, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return Problem.REVERSED;
        }
        if (granularity.periods(from, to) > MAX_PERIODS) {
            return Problem.TOO_MANY_PERIODS;
        }
        return Problem.NONE;
    }

    /** Every party, over the grouping's default range up to today, with no comparison. */
    public static PartyTrendFilter defaultFor(PartyKind kind, TrendGranularity granularity,
                                              LocalDate today) {
        return new PartyTrendFilter(kind, granularity, granularity.defaultFrom(today), today,
                null, false);
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
