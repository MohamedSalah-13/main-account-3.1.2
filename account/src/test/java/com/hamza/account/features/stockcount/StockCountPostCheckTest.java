package com.hamza.account.features.stockcount;

import com.hamza.account.features.documentdelete.DocumentDeleteStockCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What posting a count would leave below zero - {@link StockCountPostCheck}. */
class StockCountPostCheckTest {

    private static final String BRANCH = "فرع";

    /** id, item, name, code, unit, unit name, factor, the book when scanned, what was counted. */
    private static StockCountLine line(int itemId, double book, double counted) {
        return new StockCountLine(0, itemId, "item " + itemId, "C" + itemId, 1, "piece", 1, book, counted);
    }

    /** The case the warning exists for: counted 2 against a book of 5, and 4 sold before the post. */
    @Test
    @DisplayName("goods that left after the scan take the posted balance below zero")
    void goodsThatLeftAfterTheScan() {
        List<DocumentDeleteStockCheck.Shortfall> found = StockCountPostCheck.shortfalls(
                List.of(line(7, 5, 2)), Map.of(7, 1.0), BRANCH);

        assertEquals(1, found.size());
        assertEquals("item 7", found.getFirst().itemName());
        assertEquals(BRANCH, found.getFirst().stockName());
        assertEquals(-2.0, found.getFirst().remainingBase(), 0.000_001);
    }

    @Test
    @DisplayName("nothing moved since the scan: the post lands on what was counted, never below zero")
    void nothingMovedSinceTheScan() {
        assertTrue(StockCountPostCheck.shortfalls(List.of(line(7, 5, 2), line(8, 0, 0)),
                Map.of(7, 5.0, 8, 0.0), BRANCH).isEmpty());
    }

    /** A line that found no difference moves nothing, and a balance already below zero stays there. */
    @Test
    @DisplayName("a line with no difference is still named when the balance is already below zero")
    void aBalanceAlreadyBelowZero() {
        List<DocumentDeleteStockCheck.Shortfall> found = StockCountPostCheck.shortfalls(
                List.of(line(9, 3, 3)), Map.of(9, -1.0), BRANCH);

        assertEquals(1, found.size());
        assertEquals(-1.0, found.getFirst().remainingBase(), 0.000_001);
    }

    @Test
    @DisplayName("an item the warehouse holds no balance for is read as zero")
    void noBalanceIsZero() {
        assertTrue(StockCountPostCheck.shortfalls(List.of(line(4, 0, 3)), Map.of(), BRANCH).isEmpty());
    }
}
