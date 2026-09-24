package com.hamza.account.features.offers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The items an offer would sell below their cost (docs/pricing-and-offers-plan.md ق-ع٩), asked once when the
 * offer is written or switched on - never at the till. Selling rice at a loss to bring a customer in is a
 * legitimate decision; it is taken once, knowingly, and this is what lets it be.
 * <p>
 * Each item the offer reaches is judged in the unit the offer names, or its base unit, at every tier the
 * offer reaches that prices it: one of that unit at that tier's price, less what the engine would give it.
 * An item is listed at its worst tier.
 */
public final class OfferCostCheck {

    private OfferCostCheck() {
    }

    /**
     * An item in one unit, as the check reads it: its groups, how many base units the unit holds, what one
     * of it costs, and what it sells for at each tier - index 0 is tier 1; zero is no price there.
     */
    public record Candidate(int itemId, String name, int unitId, String unitName, int subGroupId,
                            int mainGroupId, BigDecimal factor, BigDecimal cost, List<BigDecimal> tierPrices) {

        public Candidate {
            Objects.requireNonNull(factor, "factor");
            Objects.requireNonNull(cost, "cost");
            tierPrices = List.copyOf(tierPrices);
        }
    }

    /** One item the offer takes below its cost: at which tier, its price, what the offer leaves, the cost. */
    public record BelowCost(int itemId, String name, String unitName, int tierId, BigDecimal price, BigDecimal net,
                            BigDecimal cost) {
    }

    /**
     * @param activeTiers the tiers in use, which an offer naming none reaches
     */
    public static List<BelowCost> below(Offer offer, List<Candidate> candidates, Set<Integer> activeTiers) {
        Set<Integer> tiers = offer.priceTierIds().isEmpty() ? activeTiers : offer.priceTierIds();
        List<BelowCost> found = new ArrayList<>();
        for (Candidate candidate : candidates) {
            BelowCost worst = null;
            for (int tier : tiers.stream().sorted().toList()) {
                if (tier < 1 || tier > candidate.tierPrices().size()) {
                    continue;
                }
                BigDecimal price = candidate.tierPrices().get(tier - 1);
                if (price == null || price.signum() <= 0) {
                    continue;
                }
                OfferEngine.Line line = new OfferEngine.Line(0, candidate.itemId(), candidate.unitId(),
                        candidate.subGroupId(), candidate.mainGroupId(), candidate.factor(), BigDecimal.ONE, price);
                var discount = OfferEngine.discountFor(offer, line);
                if (discount.isEmpty()) {
                    continue;
                }
                BigDecimal net = OfferEngine.money(price.subtract(discount.get()));
                if (net.compareTo(candidate.cost()) < 0 && (worst == null || net.compareTo(worst.net()) < 0)) {
                    worst = new BelowCost(candidate.itemId(), candidate.name(), candidate.unitName(), tier, price,
                            net, OfferEngine.money(candidate.cost()));
                }
            }
            if (worst != null) {
                found.add(worst);
            }
        }
        found.sort(Comparator.comparing(BelowCost::name, Comparator.nullsLast(Comparator.naturalOrder())));
        return found;
    }
}
