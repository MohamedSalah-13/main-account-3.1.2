package com.hamza.account.features.unitprices;

import java.util.List;

/**
 * An item with every unit it is sold in besides its own - one branch of the screen's tree.
 *
 * @param id           {@code items.id}
 * @param barcode      the item's own code
 * @param name         the item's name
 * @param baseUnitName the unit its prices are for ({@code items.unit_id})
 * @param prices       the item's own four prices
 * @param units        its other units, smallest factor first
 */
public record UnitPriceItem(int id, String barcode, String name, String baseUnitName, Prices prices,
                            List<UnitPriceLine> units) {

    public UnitPriceItem {
        prices = prices == null ? Prices.ZERO : prices;
        units = units == null ? List.of() : List.copyOf(units);
    }

    public UnitPriceLine unit(int unitId) {
        for (UnitPriceLine line : units) {
            if (line.unitId() == unitId) return line;
        }
        return null;
    }

    public UnitPriceItem withPrices(Prices next) {
        return new UnitPriceItem(id, barcode, name, baseUnitName, next, units);
    }

    public UnitPriceItem withUnits(List<UnitPriceLine> next) {
        return new UnitPriceItem(id, barcode, name, baseUnitName, prices, next);
    }
}
