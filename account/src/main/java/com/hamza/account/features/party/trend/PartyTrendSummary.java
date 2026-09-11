package com.hamza.account.features.party.trend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The chart's totals: the figures above it, summed from its own points.
 * <p>
 * Summed from the points rather than asked of the database a second time, so the cards cannot
 * describe a different set of days from the lines under them - the rule the balances footer and
 * the statement summary follow, for the same reason.
 * <p>
 * A percentage with nothing to divide by is <b>absent</b>, not zero. "Collected 0%" of nothing
 * charged is a sentence with no meaning, and a change against a year with no movement at all is
 * not "+100%" - it is not a comparison.
 */
public record PartyTrendSummary(BigDecimal debit, BigDecimal credit,
                                BigDecimal previousDebit, BigDecimal previousCredit,
                                boolean compared) {

    public static final PartyTrendSummary EMPTY = new PartyTrendSummary(
            PartyTrend.NONE, PartyTrend.NONE, PartyTrend.NONE, PartyTrend.NONE, false);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    static PartyTrendSummary of(List<PartyTrendPoint> points, boolean compared) {
        BigDecimal debit = PartyTrend.NONE;
        BigDecimal credit = PartyTrend.NONE;
        BigDecimal previousDebit = PartyTrend.NONE;
        BigDecimal previousCredit = PartyTrend.NONE;
        for (PartyTrendPoint point : points) {
            debit = debit.add(point.debit());
            credit = credit.add(point.credit());
            previousDebit = previousDebit.add(point.previousDebit());
            previousCredit = previousCredit.add(point.previousCredit());
        }
        return new PartyTrendSummary(debit, credit, previousDebit, previousCredit, compared);
    }

    /** Charged less paid over the whole range. */
    public BigDecimal net() {
        return debit.subtract(credit);
    }

    /** What share of what was charged came back as payment or credit, as a percentage. */
    public Optional<BigDecimal> collectionPercent() {
        return percentOf(credit, debit);
    }

    /** How the charged total moved against the same dates a year earlier, as a percentage. */
    public Optional<BigDecimal> debitChange() {
        return compared ? change(debit, previousDebit) : Optional.empty();
    }

    /** How the collected total moved against the same dates a year earlier, as a percentage. */
    public Optional<BigDecimal> creditChange() {
        return compared ? change(credit, previousCredit) : Optional.empty();
    }

    static Optional<BigDecimal> change(BigDecimal current, BigDecimal previous) {
        return percentOf(current.subtract(previous), previous);
    }

    private static Optional<BigDecimal> percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED).divide(whole.abs(), 2, RoundingMode.HALF_UP));
    }
}
