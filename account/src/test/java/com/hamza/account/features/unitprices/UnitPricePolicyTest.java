package com.hamza.account.features.unitprices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which prices a save may not write, stated to agree with the rules the same figures meet elsewhere. */
class UnitPricePolicyTest {

    private static UnitPriceItem item(Prices prices, UnitPriceLine... units) {
        return new UnitPriceItem(1, "", "لبن", "قطعة", prices, List.of(units));
    }

    @Test
    @DisplayName("the item's first sale price must exceed its cost - equal is a sale at cost")
    void itemSellAtCostIsRefused() {
        var rejection = UnitPricePolicy.check(item(new Prices(10, 10, 0, 0)), true, Set.of());

        assertEquals("item.error.sell.not.above.buy.named", rejection.key());
    }

    @Test
    @DisplayName("an item's second and third tiers may be unset, but not set below cost")
    void itemTiers() {
        assertNull(UnitPricePolicy.check(item(new Prices(10, 12, 0, 0)), true, Set.of()));
        assertEquals("unit.prices.error.item.tier.below.buy",
                UnitPricePolicy.check(item(new Prices(10, 12, 9, 0)), true, Set.of()).key());
    }

    @Test
    @DisplayName("a unit priced below what it costs is refused, cost worked out from the item when it has none")
    void unitBelowCost() {
        UnitPriceLine carton = new UnitPriceLine(2, "كرتونة", 12, new Prices(0, 110, 0, 0));
        var rejection = UnitPricePolicy.check(item(new Prices(10, 12, 0, 0), carton), false, Set.of(2));

        assertEquals("unit.prices.error.unit.below.buy", rejection.key());
        assertEquals(List.of("لبن", "كرتونة"), rejection.arguments());
    }

    @Test
    @DisplayName("an item stored below cost long ago does not refuse a change to one of its units")
    void untouchedItemIsNotAsked() {
        UnitPriceLine carton = new UnitPriceLine(2, "كرتونة", 12, new Prices(0, 150, 0, 0));

        assertNull(UnitPricePolicy.check(item(new Prices(10, 10, 0, 0), carton), false, Set.of(2)));
    }

    @Test
    @DisplayName("raising the item's cost asks every unit, since an automatic cost moves with it")
    void itemChangeAsksEveryUnit() {
        UnitPriceLine carton = new UnitPriceLine(2, "كرتونة", 12, new Prices(0, 130, 0, 0));

        assertNull(UnitPricePolicy.check(item(new Prices(10, 12, 0, 0), carton), false, Set.of()));
        assertEquals("unit.prices.error.unit.below.buy",
                UnitPricePolicy.check(item(new Prices(11, 12, 0, 0), carton), true, Set.of()).key());
    }

    @Test
    @DisplayName("negative, not a number, and a slipped decimal point are out of range")
    void range() {
        assertTrue(UnitPricePolicy.outOfRange(-1));
        assertTrue(UnitPricePolicy.outOfRange(Double.NaN));
        assertTrue(UnitPricePolicy.outOfRange(2_000_000_000_000.0));
        assertNull(UnitPricePolicy.rangeRejection(new PriceChange(1, 0, Prices.ZERO, new Prices(0, 5, 0, 0))));
    }
}
