package com.hamza.account.features.unitprices;

import java.util.Set;

/**
 * What the unit prices screen lists. Every item it lists has at least one unit besides its own -
 * an item with none has nothing on this screen to price.
 *
 * @param search        a name, a code or an id; blank for none
 * @param state         whether the item's units are priced by hand, from the item, or either
 * @param belowCostOnly only items with a unit that would sell at or below what it costs
 * @param itemIds       only these items, when the items screen opened this one on its ticked rows;
 *                      empty for no such limit
 */
public record UnitPriceFilter(String search, PriceState state, boolean belowCostOnly, Set<Integer> itemIds) {

    /** How an item's units are priced. */
    public enum PriceState {
        ANY,
        /** At least one unit carries a price of its own on some field. */
        MANUAL,
        /** Every unit follows the item on every field. */
        AUTOMATIC
    }

    public static final UnitPriceFilter EMPTY = new UnitPriceFilter("", PriceState.ANY, false, Set.of());

    public UnitPriceFilter {
        search = search == null ? "" : search.trim();
        state = state == null ? PriceState.ANY : state;
        itemIds = itemIds == null ? Set.of() : Set.copyOf(itemIds);
    }

    public UnitPriceFilter withSearch(String text) {
        return new UnitPriceFilter(text, state, belowCostOnly, itemIds);
    }

    public UnitPriceFilter withState(PriceState value) {
        return new UnitPriceFilter(search, value, belowCostOnly, itemIds);
    }

    public UnitPriceFilter withBelowCostOnly(boolean value) {
        return new UnitPriceFilter(search, state, value, itemIds);
    }

    public UnitPriceFilter withItemIds(Set<Integer> ids) {
        return new UnitPriceFilter(search, state, belowCostOnly, ids);
    }
}
