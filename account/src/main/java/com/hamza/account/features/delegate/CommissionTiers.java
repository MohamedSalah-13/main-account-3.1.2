package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The tiers of one commission rule, and the arithmetic over them. No database, no clock, no
 * screen: a wrong commission is an arithmetic mistake, and arithmetic is what a unit test is for.
 *
 * <p>One to three tiers, <b>lowest threshold first</b>, each saying "from this percentage of the
 * target, this rate". <b>Below the lowest threshold the commission is nothing.</b> That sentence
 * is the reason this class exists: the {@code target_delegate} view it replaces ended its
 * {@code IF} chain with an unconditional {@code rate_3}, so a delegate who reached 1% of his
 * target was paid the third rate on everything he sold, and the third threshold was a column
 * that was read and never used.
 *
 * <p>Thresholds are compared as <b>amounts</b> ({@code target x percent / 100}), never as a
 * rounded achievement percentage: 99.996% rounds to 100.00 on a screen and is still not the
 * target.
 */
public record CommissionTiers(List<Tier> tiers) {

    public static final int MAX_TIERS = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param fromPercent the share of the target at which this tier starts; zero means "from
     *                    the first pound"
     * @param ratePercent what is paid, as a percentage of the amount
     */
    public record Tier(BigDecimal fromPercent, BigDecimal ratePercent) {
        public Tier {
            if (fromPercent == null || fromPercent.signum() < 0) {
                throw new IllegalArgumentException("commission.error.tier.threshold");
            }
            if (ratePercent == null || ratePercent.signum() < 0 || ratePercent.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("commission.error.tier.rate");
            }
        }
    }

    /**
     * What a month came to.
     *
     * @param tier               the highest tier reached, counted from 1; zero when none was
     * @param ratePercent        that tier's rate; zero when none was reached
     * @param achievementPercent the amount as a percentage of the target, for display; null
     *                           when the rule has no target to measure against
     */
    public record Result(int tier, BigDecimal ratePercent, BigDecimal achievementPercent, BigDecimal amount) {
    }

    public CommissionTiers {
        if (tiers == null || tiers.isEmpty() || tiers.size() > MAX_TIERS) {
            throw new IllegalArgumentException("commission.error.tier.count");
        }
        tiers = List.copyOf(tiers);
        for (int i = 1; i < tiers.size(); i++) {
            // Strictly: two tiers starting at one threshold have no order, and which of two
            // rates a delegate is paid must not depend on the order rows came back in.
            if (tiers.get(i).fromPercent().compareTo(tiers.get(i - 1).fromPercent()) <= 0) {
                throw new IllegalArgumentException("commission.error.tier.order");
            }
        }
    }

    /**
     * Whether these tiers make sense for a rule with no target. Without a target a threshold
     * above zero can never be reached, so the only rule that means anything is a flat rate:
     * one tier, from zero.
     */
    public boolean validWithoutTarget() {
        return tiers.size() == 1 && tiers.get(0).fromPercent().signum() == 0;
    }

    /**
     * @param base   the month's amount under the rule's basis. Zero or less earns nothing: a
     *               month whose returns outweigh its sales is not a negative commission to be
     *               collected from the delegate
     * @param target the rule's target; zero means "no target"
     */
    public Result calculate(BigDecimal base, BigDecimal target, TierMode mode) {
        boolean hasTarget = target != null && target.signum() > 0;
        BigDecimal achievement = hasTarget && base != null
                ? base.multiply(HUNDRED).divide(target, 2, RoundingMode.HALF_UP)
                : null;
        if (base == null || base.signum() <= 0) {
            return new Result(0, BigDecimal.ZERO, hasTarget ? achievement : null, money(BigDecimal.ZERO));
        }

        int reached = 0;
        for (int i = 0; i < tiers.size(); i++) {
            if (base.compareTo(thresholdAmount(i, target, hasTarget)) >= 0 && reachable(i, hasTarget)) {
                reached = i + 1;
            }
        }
        if (reached == 0) {
            return new Result(0, BigDecimal.ZERO, achievement, money(BigDecimal.ZERO));
        }

        BigDecimal rate = tiers.get(reached - 1).ratePercent();
        BigDecimal amount = mode == TierMode.MARGINAL
                ? marginal(base, target, hasTarget, reached)
                : base.multiply(rate).divide(HUNDRED);
        return new Result(reached, rate, achievement, money(amount));
    }

    /** Each reached tier's rate, on the part of the amount between its threshold and the next. */
    private BigDecimal marginal(BigDecimal base, BigDecimal target, boolean hasTarget, int reached) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < reached; i++) {
            BigDecimal from = thresholdAmount(i, target, hasTarget);
            BigDecimal to = i + 1 < reached ? thresholdAmount(i + 1, target, hasTarget) : base;
            total = total.add(to.subtract(from).multiply(tiers.get(i).ratePercent()).divide(HUNDRED));
        }
        return total;
    }

    private BigDecimal thresholdAmount(int index, BigDecimal target, boolean hasTarget) {
        return hasTarget
                ? target.multiply(tiers.get(index).fromPercent()).divide(HUNDRED)
                : BigDecimal.ZERO;
    }

    /** Without a target only a tier that starts at zero can be reached. */
    private boolean reachable(int index, boolean hasTarget) {
        return hasTarget || tiers.get(index).fromPercent().signum() == 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
