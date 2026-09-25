package com.hamza.account.features.pricing;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Which tiers the "items with no price" report counts (docs/pricing-and-offers-plan.md §10.5).
 * <p>
 * V84 left all three tiers switched on, as they always had been, and on a shop that never sold at the
 * second or the third nearly every item is missing a price on both - so a report counting every tier in
 * use listed the whole catalogue, and said nothing about the items a wholesale customer would actually
 * be sold at tier 1. Switching the unused tiers off on upgrade was weighed and declined: a switched-off
 * tier leaves every combo, and a shop may keep one for a reason no table records (labels printed at the
 * wholesale price, say). So nothing is switched off, and the report counts the tiers somebody is sold at:
 * <ul>
 *   <li><b>tier 1 always</b> - every item must carry it, and it is what a missing price falls back to;</li>
 *   <li><b>a tier in use with an active customer on it</b>;</li>
 *   <li>and a tier in use with <b>nobody</b> on it only when asked for - it is named instead, with the two
 *       roads out: switch it off, or fill it with a rule.</li>
 * </ul>
 * A switched-off tier is never counted: nobody is sold at it.
 *
 * @param counted     the tiers whose missing prices the report lists, lowest first
 * @param idle        the tiers in use that no active customer is on
 * @param includeIdle whether {@code idle} was counted all the same
 */
public record MissingPriceScope(List<Integer> counted, List<PriceTier> idle, boolean includeIdle) {

    public MissingPriceScope {
        counted = List.copyOf(counted);
        idle = List.copyOf(idle);
    }

    /**
     * @param customersByTier how many active customers each tier has; a tier absent from it has none
     * @param includeIdle     count the tiers in use that nobody is on as well
     */
    public static MissingPriceScope of(PriceTierCatalog catalog, Map<Integer, Integer> customersByTier,
                                       boolean includeIdle) {
        List<PriceTier> active = catalog.active();
        List<PriceTier> idle = active.stream()
                .filter(tier -> !tier.isFirst())
                .filter(tier -> customersByTier.getOrDefault(tier.id(), 0) <= 0)
                .toList();
        List<Integer> counted = active.stream()
                .filter(tier -> includeIdle || !idle.contains(tier))
                .map(PriceTier::id)
                .toList();
        if (!counted.contains(PriceTiers.FIRST)) {
            // A catalogue read with no row for tier 1 still has the price every item must carry.
            counted = Stream.concat(Stream.of(PriceTiers.FIRST), counted.stream())
                    .toList();
        }
        return new MissingPriceScope(counted, idle, includeIdle);
    }

    /** True when a tier in use was left out, and the screen has to say which and why. */
    public boolean leftOut() {
        return !includeIdle && !idle.isEmpty();
    }
}
