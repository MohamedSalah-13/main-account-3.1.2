package com.hamza.account.features.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceTierCatalogTest {

    static PriceTierCatalog tiers(boolean secondActive, boolean thirdActive) {
        // Deliberately out of order: the names used to be read by position from a query with no ORDER BY.
        return new PriceTierCatalog(List.of(
                new PriceTier(3, "جملة", thirdActive, null),
                new PriceTier(1, "قطاعي", true, null),
                new PriceTier(2, "تجزئة", secondActive, null)));
    }

    @Test
    @DisplayName("names are by the tier's id, not by where the row came in the result")
    void byId() {
        PriceTierCatalog catalog = tiers(true, true);
        assertEquals("قطاعي", catalog.name(1));
        assertEquals("جملة", catalog.name(3));
        assertEquals(List.of(1, 2, 3), catalog.all().stream().map(PriceTier::id).toList());
        assertEquals("7", catalog.name(7), "a tier with no row reads as its number");
    }

    @Test
    @DisplayName("a tier switched off leaves the combo, unless a record already names it")
    void activeAndChoices() {
        PriceTierCatalog catalog = tiers(false, true);
        assertEquals(List.of(1, 3), catalog.active().stream().map(PriceTier::id).toList());
        assertEquals(List.of(1, 2, 3), catalog.choicesIncluding(2).stream().map(PriceTier::id).toList());
        assertEquals(List.of(1, 3), catalog.choicesIncluding(3).stream().map(PriceTier::id).toList());
    }

    @Test
    @DisplayName("a customer on a tier switched off is priced at tier 1")
    void forCustomer() {
        PriceTierCatalog catalog = tiers(false, true);
        assertEquals(3, catalog.forCustomer(3));
        assertEquals(1, catalog.forCustomer(2));
        assertEquals(1, catalog.forCustomer(0));
    }
}
