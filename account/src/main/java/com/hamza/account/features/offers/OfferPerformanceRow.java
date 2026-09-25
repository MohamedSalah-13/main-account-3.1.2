package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * One offer in the performance report: what its lines came to over the period, and over the period before
 * it - the same days of the month before, or as many days straight before (the profit and loss's rule).
 */
public record OfferPerformanceRow(Offer offer, OfferFigures now, OfferFigures before) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public OfferPerformanceRow {
        Objects.requireNonNull(offer, "offer");
        now = now == null ? OfferFigures.NONE : now;
        before = before == null ? OfferFigures.NONE : before;
    }

    /**
     * The times the offer was given in the period, to three places: units for a price offer, groups for a
     * quantity offer, bundles for a bundle, invoices for an invoice offer - what its limits count.
     */
    public BigDecimal times() {
        BigDecimal group = offer.groupSize();
        return group == null || group.signum() <= 0 ? BigDecimal.ZERO
                : now.covered().divide(group, 3, RoundingMode.HALF_UP);
    }

    /** The net against the period before, in per cent; empty when there was nothing before to compare. */
    public Optional<BigDecimal> netChange() {
        return change(now.net(), before.net());
    }

    /** The discount given against the period before, in per cent; empty with nothing given before. */
    public Optional<BigDecimal> discountChange() {
        return change(now.discount(), before.discount());
    }

    static Optional<BigDecimal> change(BigDecimal now, BigDecimal before) {
        if (before.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(now.subtract(before).multiply(HUNDRED).divide(before, 2, RoundingMode.HALF_UP));
    }
}
