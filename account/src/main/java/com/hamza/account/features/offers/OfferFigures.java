package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What an offer's lines came to over a period - the sales dated in it less the returns dated in it
 * (docs/pricing-and-offers-plan.md phase E).
 *
 * @param invoices         the sales naming it
 * @param lines            the sale lines naming it
 * @param soldQuantity     base units on those lines
 * @param returnedQuantity base units on the returns naming it, positive
 * @param covered          the units the offer covered, less those the returns gave back - its times are
 *                         these over its group
 * @param given            the offer's part of the sale lines' discounts
 * @param givenBack        the returns' share of it, positive
 * @param sold             the sale lines, each after its own discount
 * @param returned         the return lines, each after its own discount, positive
 * @param cost             the lines' recorded cost less the returns' - null when the reader may not see a
 *                         profit, and then it was never read
 */
public record OfferFigures(int invoices, int lines, BigDecimal soldQuantity, BigDecimal returnedQuantity,
                           BigDecimal covered, BigDecimal given, BigDecimal givenBack, BigDecimal sold,
                           BigDecimal returned, BigDecimal cost) {

    public static final OfferFigures NONE = new OfferFigures(0, 0, null, null, null, null, null, null, null, null);

    public OfferFigures {
        soldQuantity = orZero(soldQuantity);
        returnedQuantity = orZero(returnedQuantity);
        covered = orZero(covered);
        given = orZero(given);
        givenBack = orZero(givenBack);
        sold = orZero(sold);
        returned = orZero(returned);
    }

    /** The discount the offer gave that stayed given. */
    public BigDecimal discount() {
        return given.subtract(givenBack);
    }

    /** What its lines sold for, less what was refunded on them. */
    public BigDecimal net() {
        return sold.subtract(returned);
    }

    /** The net less the cost; empty when the cost was not read. */
    public Optional<BigDecimal> profit() {
        return cost == null ? Optional.empty() : Optional.of(net().subtract(cost));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
