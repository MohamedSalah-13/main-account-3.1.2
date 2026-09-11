package com.hamza.account.features.items;

import com.hamza.account.features.items.ItemCatalogFilter.BalanceRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A saved filter outlives the code that wrote it, in both directions: a range added to the filter
 * has to survive being saved, and a filter saved before the range existed has to read back
 * without one rather than as nothing at all.
 */
class SavedItemFiltersTest {

    @Test
    @DisplayName("a minimum-quantity range survives being saved and read back")
    void theRangeRoundTrips() {
        ItemCatalogFilter filter = ItemCatalogFilter.EMPTY
                .withBalance(BalanceRule.BELOW_MINIMUM)
                .withMiniQuantityBetween(0.0, 1.0);

        assertEquals(filter, SavedItemFilters.decode(SavedItemFilters.encode(filter)));
    }

    @Test
    @DisplayName("a filter saved before the range existed reads back with no range, and the rest intact")
    void anOlderFilterStillReads() {
        ItemCatalogFilter decoded = SavedItemFilters.decode("scope=ANY;match=AUTO;balance=NEGATIVE;minPrice=5.0;");

        assertEquals(BalanceRule.NEGATIVE, decoded.balance());
        assertEquals(5.0, decoded.minSellPrice());
        assertNull(decoded.miniQuantityFrom());
        assertNull(decoded.miniQuantityTo());
    }
}
