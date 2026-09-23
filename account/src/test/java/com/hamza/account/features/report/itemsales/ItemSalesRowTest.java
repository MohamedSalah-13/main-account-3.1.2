package com.hamza.account.features.report.itemsales;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSalesRowTest {

    /** A row with the figures as written, and a cost - or none, as for a reader who may not see a profit. */
    static ItemSalesRow row(int id, String name, String sold, String returned, String soldAmount,
                            String returnedAmount, String cost) {
        return new ItemSalesRow(id, name, null, "قطعة", new BigDecimal(sold), new BigDecimal(returned), 1,
                new BigDecimal(soldAmount), new BigDecimal(returnedAmount), cost == null ? null : new BigDecimal(cost));
    }

    @Test
    @DisplayName("what stayed sold is what was sold less what came back, in units and in money")
    void theNetTakesTheReturnsOff() {
        ItemSalesRow rice = row(1, "rice", "10", "1", "600", "60", "405");

        assertEquals(new BigDecimal("9"), rice.netQuantity());
        assertEquals(new BigDecimal("540"), rice.net());
        assertEquals(Optional.of(new BigDecimal("135")), rice.margin());
        assertEquals(Optional.of(new BigDecimal("25.00")), rice.marginPercent());
    }

    @Test
    @DisplayName("a cost that was not read makes no margin, and nothing sold makes no percentage")
    void noCostNoMargin() {
        assertTrue(row(1, "rice", "10", "0", "600", "0", null).margin().isEmpty());
        assertTrue(row(1, "rice", "10", "0", "600", "0", null).marginPercent().isEmpty());
        assertTrue(row(2, "oil", "0", "2", "0", "80", "-60").marginPercent().isEmpty(),
                "a period of nothing but a return has nothing to divide by");
        assertEquals(Optional.of(new BigDecimal("-20")), row(2, "oil", "0", "2", "0", "80", "-60").margin());
    }

    @Test
    @DisplayName("a missing name, unit or figure is blank or zero, never a null on screen")
    void blanksAreFilled() {
        ItemSalesRow row = new ItemSalesRow(3, null, null, null, null, null, 0, null, null, null);
        assertEquals("", row.name());
        assertEquals("", row.unitName());
        assertEquals(BigDecimal.ZERO, row.net());
    }
}
