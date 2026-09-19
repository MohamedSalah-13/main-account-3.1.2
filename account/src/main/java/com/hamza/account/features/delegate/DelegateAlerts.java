package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * The two things about commission somebody has to be told, decided over plain values - no
 * database, no clock, no notification engine. The sources that poll are thin around this, and
 * every boundary here has a test, because a reminder that fires on the wrong day is one people
 * learn to dismiss.
 */
public final class DelegateAlerts {

    /**
     * A projection over fewer days than this says more about which weekday the month began on
     * than about the delegate. Two thirds of a month is also late enough to be worth acting on
     * and early enough that acting can still change the figure.
     */
    public static final int FIRST_DAY_TO_JUDGE_PACE = 20;

    private DelegateAlerts() {
    }

    /**
     * The month that is over and has not been approved: last month, when there would be
     * something to approve in it. A month in which no delegate has a rule is nobody's reminder -
     * which is every shop that does not use commission, so this is silent for them for ever.
     *
     * @param lastMonthApproved   whether last month has an approved run
     * @param lastMonthWouldWrite how many lines approving it now would write
     */
    public static Optional<YearMonth> monthAwaitingApproval(LocalDate today, boolean lastMonthApproved,
                                                          int lastMonthWouldWrite) {
        if (lastMonthApproved || lastMonthWouldWrite <= 0) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.from(today).minusMonths(1));
    }

    /**
     * A delegate whose month, carried on at the pace it has kept so far, ends below the lowest
     * tier of his rule - so at the rate he is going he earns no commission at all.
     *
     * @param base      what his rule is a percentage of, so far this month
     * @param projected the same figure at month end, at this pace
     * @param needed    the lowest tier's threshold, as an amount
     */
    public record Lagging(int employeeId, String name, BigDecimal base, BigDecimal projected, BigDecimal needed) {
    }

    /**
     * Judged only from {@link #FIRST_DAY_TO_JUDGE_PACE} on, only for a rule with a target - a
     * flat rate has nothing to fall short of - and only for an active delegate: somebody who has
     * left is not behind on anything.
     */
    public static List<Lagging> lagging(List<DelegatePerformanceRow> currentMonth, LocalDate today) {
        if (today.getDayOfMonth() < FIRST_DAY_TO_JUDGE_PACE) {
            return List.of();
        }
        return currentMonth.stream()
                .filter(row -> row.activity().active() && row.rule().isPresent() && row.rule().get().hasTarget())
                .map(row -> judge(row, today))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Lagging> judge(DelegatePerformanceRow row, LocalDate today) {
        CommissionRule rule = row.rule().orElseThrow();
        BigDecimal needed = rule.target()
                .multiply(rule.tiers().tiers().get(0).fromPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal base = row.base();
        BigDecimal projected = projected(base, today);
        if (needed.signum() <= 0 || projected.compareTo(needed) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Lagging(row.activity().employeeId(), row.name(), base, projected, needed));
    }

    /**
     * The month-end figure at the pace kept so far: {@code base / days elapsed x days in month}.
     * A base of zero or less projects to itself - there is no pace to extend.
     */
    static BigDecimal projected(BigDecimal base, LocalDate today) {
        if (base == null || base.signum() <= 0) {
            return base == null ? BigDecimal.ZERO : base;
        }
        return base.multiply(BigDecimal.valueOf(today.lengthOfMonth()))
                .divide(BigDecimal.valueOf(today.getDayOfMonth()), 2, RoundingMode.HALF_UP);
    }
}
