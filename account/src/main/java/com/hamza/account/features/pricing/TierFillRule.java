package com.hamza.account.features.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * How a tier's prices are filled from another figure: "wholesale is retail less 5%, rounded to the
 * nearest quarter", or "cost plus 12%" (V84, docs/pricing-and-offers-plan.md ق-س٥).
 * <p>
 * <b>A rule writes prices; it is never worked out at the till.</b> Worked out at the moment of sale,
 * a price would have two definitions - the one written on the item and the one computed - and nobody
 * could say which a document read. So the rule is applied on the unit prices screen, previewed and
 * confirmed, and what it wrote is what is sold; it is also offered as a grey suggestion on the item
 * screen. Changing a rule moves no price until somebody applies it again.
 *
 * @param source       what the prices are worked out from
 * @param sourceTierId the tier they are worked out from, for {@link Source#TIER}; null for the cost
 * @param percent      added to the source ({@code -5} is five percent less); greater than -100
 * @param rounding     the result is rounded to the nearest multiple of this, half up; greater than zero
 */
public record TierFillRule(Source source, Integer sourceTierId, BigDecimal percent, BigDecimal rounding) {

    public enum Source {
        /** The item's (or the unit's) cost. */
        COST,
        /** Another tier's price. */
        TIER
    }

    /** The roundings the screen offers; any positive multiple is accepted from the database. */
    public static final java.util.List<BigDecimal> ROUNDINGS = java.util.List.of(
            new BigDecimal("0.01"), new BigDecimal("0.05"), new BigDecimal("0.25"), new BigDecimal("0.50"),
            new BigDecimal("1.00"), new BigDecimal("5.00"));

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public TierFillRule {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(percent, "percent");
        Objects.requireNonNull(rounding, "rounding");
        if (source == Source.TIER && (sourceTierId == null || !PriceTiers.exists(sourceTierId))) {
            throw new IllegalArgumentException("A rule over a tier names an existing tier");
        }
        if (source == Source.COST && sourceTierId != null) {
            throw new IllegalArgumentException("A rule over the cost names no tier");
        }
        if (percent.compareTo(HUNDRED.negate()) <= 0) {
            throw new IllegalArgumentException("percent must be greater than -100");
        }
        if (rounding.signum() <= 0) {
            throw new IllegalArgumentException("rounding must be greater than zero");
        }
    }

    public static TierFillRule fromCost(BigDecimal percent, BigDecimal rounding) {
        return new TierFillRule(Source.COST, null, percent, rounding);
    }

    public static TierFillRule fromTier(int tierId, BigDecimal percent, BigDecimal rounding) {
        return new TierFillRule(Source.TIER, tierId, percent, rounding);
    }

    /**
     * The price this rule gives a source figure: {@code source × (100 + percent) / 100}, rounded to the
     * nearest multiple of {@link #rounding}, half up, and never below zero. A source of zero or less has
     * nothing to be worked out from, and answers null - a price is never filled with a zero.
     */
    public BigDecimal apply(BigDecimal sourcePrice) {
        if (sourcePrice == null || sourcePrice.signum() <= 0) {
            return null;
        }
        BigDecimal raw = sourcePrice.multiply(HUNDRED.add(percent)).divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal steps = raw.divide(rounding, 0, RoundingMode.HALF_UP);
        BigDecimal rounded = steps.multiply(rounding).setScale(2, RoundingMode.HALF_UP);
        return rounded.signum() <= 0 ? null : rounded;
    }
}
