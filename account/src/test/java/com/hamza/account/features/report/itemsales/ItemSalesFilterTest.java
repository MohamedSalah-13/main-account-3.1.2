package com.hamza.account.features.report.itemsales;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSalesFilterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 23);

    @Test
    @DisplayName("a period needs both ends, the first not after the last")
    void thePeriodIsChecked() {
        assertThrows(UserValidationException.class, () -> ItemSalesFilter.fromScreen(null, DAY, ""));
        assertThrows(UserValidationException.class, () -> ItemSalesFilter.fromScreen(DAY, DAY.minusDays(1), ""));
        assertThrows(IllegalArgumentException.class, () -> new ItemSalesFilter(DAY, DAY.minusDays(1), ""));
        assertEquals(DAY, ItemSalesFilter.of(DAY, DAY).to(), "one day is a period");
    }

    @Test
    @DisplayName("a text narrows the items and is the items list's own search; blank narrows nothing")
    void theTextNarrows() {
        assertFalse(new ItemSalesFilter(DAY, DAY, "   ").narrows());
        assertFalse(new ItemSalesFilter(DAY, DAY, null).narrows());
        ItemSalesFilter rice = new ItemSalesFilter(DAY, DAY, " rice ");
        assertTrue(rice.narrows());
        assertEquals("rice", rice.catalogFilter().searchText());
    }
}
