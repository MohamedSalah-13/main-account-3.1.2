package com.hamza.account.features.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What applying a tier's fill rule would write (V84, docs/pricing-and-offers-plan.md ق-س٥), worked out
 * over plain figures - no database, no screen.
 * <p>
 * <b>The item's own price on the tier</b> is the rule applied to the item's cost or to its price on the
 * source tier. <b>A unit's own price on the tier</b> is written only where the unit carries its own
 * source figure - its own cost, or its own price on the source tier: a carton priced by hand on the retail
 * tier gets a wholesale price of its own worked out from that, while a carton that follows the item keeps
 * following it, and the item's new price reaches it times its factor. A figure with nothing to work from
 * is left alone, and a figure the rule would not change is not a change.
 */
public final class TierFill {

    /** One unit of an item as the fill reads it: its own figures, zero where it follows the item. */
    public record UnitSource(int unitId, String unitName, BigDecimal ownBuy, List<BigDecimal> ownSell) {

        public UnitSource {
            ownSell = List.copyOf(ownSell);
        }
    }

    /** One item as the fill reads it: its cost, its price on each tier (tier 1 first) and its other units. */
    public record ItemSource(int itemId, String name, String baseUnit, BigDecimal buy, List<BigDecimal> sell,
                             List<UnitSource> units) {

        public ItemSource {
            sell = List.copyOf(sell);
            units = List.copyOf(units);
        }
    }

    /**
     * One figure the fill would write.
     *
     * @param unitId {@link #ITEM} for the item's own price, else the unit whose own price it is
     */
    public record Change(int itemId, int unitId, String itemName, String unitName, BigDecimal before, BigDecimal after) {

        public boolean onUnit() {
            return unitId != ITEM;
        }
    }

    /** The unit id a change on the item's own price carries. */
    public static final int ITEM = 0;

    private TierFill() {
    }

    public static List<Change> changes(List<ItemSource> items, int tierId, TierFillRule rule) {
        return changes(items, tierId, rule, false);
    }

    /**
     * @param onlyMissing leave every figure the tier already has - fill the gaps and nothing else. A
     *                    wholesale price somebody typed is a decision, and a rule applied over the whole
     *                    catalogue would overwrite it; the screen offers this, and offers it first.
     */
    public static List<Change> changes(List<ItemSource> items, int tierId, TierFillRule rule, boolean onlyMissing) {
        Objects.requireNonNull(rule, "rule");
        if (!PriceTiers.exists(tierId)) {
            throw new IllegalArgumentException("Unknown price tier " + tierId);
        }
        List<Change> changes = new ArrayList<>();
        for (ItemSource item : items) {
            BigDecimal source = rule.source() == TierFillRule.Source.COST
                    ? item.buy() : item.sell().get(rule.sourceTierId() - 1);
            add(changes, onlyMissing, item.itemId(), ITEM, item.name(), item.baseUnit(),
                    item.sell().get(tierId - 1), rule.apply(money(source)));
            for (UnitSource unit : item.units()) {
                BigDecimal own = rule.source() == TierFillRule.Source.COST
                        ? unit.ownBuy() : unit.ownSell().get(rule.sourceTierId() - 1);
                if (own == null || own.signum() <= 0) {
                    continue;
                }
                add(changes, onlyMissing, item.itemId(), unit.unitId(), item.name(), unit.unitName(),
                        unit.ownSell().get(tierId - 1), rule.apply(money(own)));
            }
        }
        return changes;
    }

    private static void add(List<Change> changes, boolean onlyMissing, int itemId, int unitId, String name, String unit,
                            BigDecimal before, BigDecimal after) {
        BigDecimal stored = money(before);
        if (after != null && stored.compareTo(after) != 0 && !(onlyMissing && stored.signum() > 0)) {
            changes.add(new Change(itemId, unitId, name, unit, stored, after));
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
