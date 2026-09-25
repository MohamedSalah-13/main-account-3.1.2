package com.hamza.account.features.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingPriceScopeTest {

    private static List<Integer> idle(MissingPriceScope scope) {
        return scope.idle().stream().map(PriceTier::id).toList();
    }

    @Test
    @DisplayName("a shop selling at tier 1 alone counts tier 1 and names the two it left out")
    void everyCustomerOnTierOne() {
        // The development data of §10.5: all three switched on, every customer on tier 1.
        MissingPriceScope scope = MissingPriceScope.of(PriceTierCatalogTest.tiers(true, true), Map.of(1, 147), false);

        assertEquals(List.of(1), scope.counted());
        assertEquals(List.of(2, 3), idle(scope));
        assertTrue(scope.leftOut());
    }

    @Test
    @DisplayName("a tier with a customer on it is counted, and one with nobody is not")
    void aTierWithACustomer() {
        MissingPriceScope scope = MissingPriceScope.of(PriceTierCatalogTest.tiers(true, true), Map.of(1, 40, 3, 2), false);

        assertEquals(List.of(1, 3), scope.counted());
        assertEquals(List.of(2), idle(scope));
    }

    @Test
    @DisplayName("asked for, the idle tiers are counted too - and nothing is said to be left out")
    void includeIdle() {
        MissingPriceScope scope = MissingPriceScope.of(PriceTierCatalogTest.tiers(true, true), Map.of(1, 147), true);

        assertEquals(List.of(1, 2, 3), scope.counted());
        assertEquals(List.of(2, 3), idle(scope), "still named, so the screen keeps its switch");
        assertFalse(scope.leftOut());
    }

    @Test
    @DisplayName("a switched-off tier is neither counted nor idle: nobody is sold at it")
    void switchedOff() {
        MissingPriceScope scope = MissingPriceScope.of(PriceTierCatalogTest.tiers(false, true), Map.of(1, 5), true);

        assertEquals(List.of(1, 3), scope.counted());
        assertEquals(List.of(3), idle(scope));
    }

    @Test
    @DisplayName("tier 1 is counted with no customer on it - every item must carry it")
    void tierOneWithNobody() {
        MissingPriceScope scope = MissingPriceScope.of(PriceTierCatalogTest.tiers(true, false), Map.of(2, 3), false);

        assertEquals(List.of(1, 2), scope.counted());
        assertEquals(List.of(), idle(scope));
        assertFalse(scope.leftOut());
    }

    @Test
    @DisplayName("a catalogue read with no row for tier 1 still counts it")
    void noRowForTierOne() {
        PriceTierCatalog catalog = new PriceTierCatalog(List.of(new PriceTier(2, "جملة", true, null)));

        assertEquals(List.of(1, 2), MissingPriceScope.of(catalog, Map.of(2, 1), false).counted());
    }
}
