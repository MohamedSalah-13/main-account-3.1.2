package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * What an offer says about one unit of an item, for the shelf label and the price-check screen
 * (docs/pricing-and-offers-plan.md phase E). Asked of the offers in force on a day at a tier, never of the
 * database: the engine's own rules decide, so the shelf cannot promise what the till will not give.
 * <p>
 * <b>A price for one unit, where an offer gives one</b> - a percentage, an amount off, a price for the unit -
 * the line of one unit run through {@link OfferEngine#discountFor}: the label prints it beside the list price
 * struck through, and the kiosk says it. Of those that reach the unit, the highest priority, then the one
 * giving more, then the oldest - the engine's own order within a kind.
 * <p>
 * <b>Otherwise an offer to be told, with no price</b> - "3 for 100", "buy 2 get 1", a bundle the item is a
 * component of: none of them changes what one unit costs, so none is a price on a label; the kiosk says it in
 * words. An offer on the invoice's total is neither: it is about the invoice, and would stand beside every item
 * in the shop.
 *
 * @param offerPrice what one unit comes to under the offer, or null for an offer that has no price for one unit
 */
public record OfferPriceTag(Offer offer, BigDecimal listPrice, BigDecimal offerPrice) {

    public OfferPriceTag {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(listPrice, "listPrice");
    }

    /** Whether the tag is a price for one unit - what a label can print. */
    public boolean hasPrice() {
        return offerPrice != null;
    }

    /**
     * The tag for one unit of an item at its list price, or none.
     *
     * @param oneUnit the item in its unit, with its groups, a quantity of one and its price at the tier
     */
    public static Optional<OfferPriceTag> of(Collection<Offer> offers, OfferEngine.Line oneUnit, LocalDate day,
                                             Integer tierId) {
        OfferEngine.Line line = new OfferEngine.Line(oneUnit.index(), oneUnit.itemId(), oneUnit.unitId(),
                oneUnit.subGroupId(), oneUnit.mainGroupId(), oneUnit.factor(), BigDecimal.ONE, oneUnit.price());
        OfferPriceTag best = null;
        for (Offer offer : offers) {
            if (!offer.kind().single() || !offer.reaches(day, tierId, false)) {
                continue;
            }
            BigDecimal discount = OfferEngine.discountFor(offer, line).orElse(BigDecimal.ZERO);
            if (discount.signum() <= 0) {
                continue;
            }
            OfferPriceTag candidate = new OfferPriceTag(offer, OfferEngine.money(line.price()),
                    OfferEngine.money(line.price().subtract(discount)));
            if (best == null || PRICED.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        return offers.stream()
                .filter(offer -> offer.kind().pooled() && offer.reaches(day, tierId, false) && told(offer, line))
                .min(Comparator.comparingInt(Offer::priority).reversed().thenComparingInt(Offer::id))
                .map(offer -> new OfferPriceTag(offer, OfferEngine.money(line.price()), null));
    }

    /** The highest priority, then the lower price, then the oldest offer. */
    private static final Comparator<OfferPriceTag> PRICED = Comparator
            .<OfferPriceTag>comparingInt(tag -> tag.offer().priority()).reversed()
            .thenComparing(OfferPriceTag::offerPrice)
            .thenComparingInt(tag -> tag.offer().id());

    /** Whether a pooled offer is about this unit: it earns it, in the unit the offer counts, or it is a component. */
    private static boolean told(Offer offer, OfferEngine.Line line) {
        if (offer.kind() == OfferKind.BUNDLE) {
            return offer.components().stream().anyMatch(component -> component.names(line));
        }
        return offer.targets(line) && (offer.unitId() == null || offer.unitId() == line.unitId());
    }
}
