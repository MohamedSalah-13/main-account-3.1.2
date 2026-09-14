package com.hamza.account.features.unitprices;

import com.hamza.account.features.items.ItemQuickEditField;

import java.util.List;

/**
 * What a price save must not write.
 * <p>
 * Stated to agree with the rules already applied to the same figures elsewhere, so a price this
 * screen accepts is not one another screen then refuses:
 * <ul>
 *   <li>an item's first sale price above its cost - {@code ItemsService.requireSellAboveBuy};</li>
 *   <li>its second and third either unset or above cost - {@link ItemQuickEditField};</li>
 *   <li>a unit, on every tier it has a price for, above what that unit costs - the floor
 *       {@code ItemUnits.buyPrice} sets for a sale at the till, worked out the way
 *       {@link UnitPriceLine#effective} works it out.</li>
 * </ul>
 */
public final class UnitPricePolicy {

    private UnitPricePolicy() {
    }

    /** A refusal: a message key and the values its {@code %s} placeholders take. */
    public record Rejection(String key, List<Object> arguments) {
    }

    /** A figure no price field may hold - negative, not a number, or a slipped decimal point. */
    public static boolean outOfRange(double value) {
        return !Double.isFinite(value) || value < 0 || value > ItemQuickEditField.MAXIMUM;
    }

    /** Whether any figure a change would write is out of range. */
    public static Rejection rangeRejection(PriceChange change) {
        for (PriceField field : PriceField.values()) {
            if (outOfRange(change.after().get(field))) {
                return new Rejection("unit.prices.error.range", List.of());
            }
        }
        return null;
    }

    /**
     * The first rule {@code item}, as it would stand after the save, breaks.
     *
     * @param itemChanged    whether the save changes the item's own prices. Only then is the item's
     *                       own rule asked - an item stored below cost years ago is not a reason to
     *                       refuse a carton's new price - and only then are all of its units asked,
     *                       since a unit priced from the item moves with it
     * @param changedUnitIds the units the save changes; each is asked whatever happened to the item
     */
    public static Rejection check(UnitPriceItem item, boolean itemChanged, java.util.Set<Integer> changedUnitIds) {
        Prices prices = item.prices();
        if (itemChanged) {
            if (prices.sell1() <= prices.buy()) {
                return new Rejection("item.error.sell.not.above.buy.named", List.of(name(item)));
            }
            if (tierBelowCost(prices.sell2(), prices.buy()) || tierBelowCost(prices.sell3(), prices.buy())) {
                return new Rejection("unit.prices.error.item.tier.below.buy", List.of(name(item)));
            }
        }
        for (UnitPriceLine unit : item.units()) {
            if (!itemChanged && !changedUnitIds.contains(unit.unitId())) continue;
            double cost = unit.effective(PriceField.BUY, prices);
            for (PriceField field : PriceField.values()) {
                if (!field.isSell()) continue;
                if (tierBelowCost(unit.effective(field, prices), cost)) {
                    return new Rejection("unit.prices.error.unit.below.buy",
                            List.of(name(item), unit.unitName() == null ? "" : unit.unitName()));
                }
            }
        }
        return null;
    }

    /** Zero is a tier with no price, never a sale below cost. */
    private static boolean tierBelowCost(double sell, double cost) {
        return sell > 0 && sell <= cost;
    }

    private static String name(UnitPriceItem item) {
        return item.name() == null ? String.valueOf(item.id()) : item.name();
    }
}
