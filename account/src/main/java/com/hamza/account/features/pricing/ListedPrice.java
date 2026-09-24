package com.hamza.account.features.pricing;

/**
 * What the list says one of a unit sells for on a tier, and where the figure came from.
 *
 * @param price    per unit, in the base; zero only when the item has no price even on tier 1
 * @param tierId   the tier that was asked for
 * @param source   where the figure came from - {@link Source#FIRST_TIER} when the tier asked for had
 *                 none and the item's tier-1 price stands in for it (docs/pricing-and-offers-plan.md ق-س٣)
 */
public record ListedPrice(double price, int tierId, Source source) {

    public enum Source {
        /** The unit carries its own price on the tier. */
        UNIT_OWN,
        /** The item's price on the tier times the unit's factor. */
        ITEM_TIMES_FACTOR,
        /** The tier asked for had no price, so this is tier 1's - and the line says so. */
        FIRST_TIER
    }

    public ListedPrice {
        if (source == null) {
            throw new IllegalArgumentException("source");
        }
    }

    /** Whether the figure stands in for a price the tier asked for does not have. */
    public boolean fromFirstTier() {
        return source == Source.FIRST_TIER;
    }
}
