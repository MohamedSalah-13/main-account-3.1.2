package com.hamza.account.features.unitprices;

import java.util.List;

/**
 * One page of the screen and how many items the filter matches in all.
 *
 * @param costVisible whether the reader may see costs; when not, every cost in {@code items} is zero
 */
public record UnitPricePage(List<UnitPriceItem> items, int total, boolean costVisible) {

    public UnitPricePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
