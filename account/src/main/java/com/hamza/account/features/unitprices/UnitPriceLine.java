package com.hamza.account.features.unitprices;

import com.hamza.account.features.items.UnitPriceSuggestion;

/**
 * One unit an item is sold in besides its own: a row of {@code items_units}.
 *
 * @param unitId   {@code items_units.unit}
 * @param unitName what the unit is called
 * @param factor   how many base units one of it holds ({@code items_units.quantity})
 * @param own      the prices this unit carries of its own; {@code 0} in a field means none
 */
public record UnitPriceLine(int unitId, String unitName, double factor, Prices own) {

    public UnitPriceLine {
        own = own == null ? Prices.ZERO : own;
    }

    /**
     * What the unit costs or sells for on {@code field}: its own price where it has one, and
     * otherwise the item's price times the factor.
     * <p>
     * The arithmetic is {@link UnitPriceSuggestion#forFactor} - the grey figure the units tab
     * of the item screen already shows - so the two screens cannot quote a carton differently.
     * It is also what {@code ItemUnits.sellPrice} charges at the till, to the cent.
     */
    public double effective(PriceField field, Prices item) {
        double own = this.own.get(field);
        return own > 0 ? own : UnitPriceSuggestion.forFactor(item.get(field), factor);
    }

    /** Whether {@code field} follows the item rather than being set by hand. */
    public boolean isAutomatic(PriceField field) {
        return own.get(field) <= 0;
    }

    public UnitPriceLine withOwn(Prices prices) {
        return new UnitPriceLine(unitId, unitName, factor, prices);
    }
}
