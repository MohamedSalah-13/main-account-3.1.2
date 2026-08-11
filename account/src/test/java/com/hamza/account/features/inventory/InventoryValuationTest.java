package com.hamza.account.features.inventory;

import com.hamza.account.features.notification.StockLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a row is worth, and which state of the stock it belongs to.
 * <p>
 * Both used to be spread out - the valuation was computed inside the DAO's row
 * mapper, and "is this item low" existed twice, once for the sheet and once for the
 * sales alert. Neither could be checked without a database.
 */
class InventoryValuationTest {

    private static InventoryRow row(double balance, double buyPrice, double sellPrice, double miniQuantity) {
        return new InventoryRow(1, "سكر", "123", "كيلو", true,
                0, 0, 0, 0, 0, 0, 0, 0,
                balance, buyPrice, sellPrice, miniQuantity);
    }

    @Nested
    @DisplayName("Valuation")
    class Valuation {

        @Test
        @DisplayName("value is the balance at the item's own price")
        void valuesAtBothPrices() {
            InventoryRow row = row(12, 10.5, 14.25, 0);

            assertEquals(126.0, row.valueAtCost());
            assertEquals(171.0, row.valueAtSale());
        }

        @Test
        @DisplayName("a negative balance carries a negative value, it is not clamped")
        void negativeBalanceIsNotHidden() {
            InventoryRow row = row(-3, 10, 20, 0);

            assertEquals(-30.0, row.valueAtCost());
            assertEquals(-60.0, row.valueAtSale());
        }

        @Test
        @DisplayName("an item with no price is worth nothing, not NaN")
        void noPriceIsZero() {
            InventoryRow row = row(100, 0, 0, 0);

            assertEquals(0.0, row.valueAtCost());
            assertEquals(0.0, row.valueAtSale());
        }

        @Test
        @DisplayName("value is rounded to two places")
        void roundsToTwoPlaces() {
            // 3 x 0.335 = 1.005, which must not be written as 1.00499999...
            assertEquals(1.01, row(3, 0.335, 0, 0).valueAtCost());
        }
    }

    @Nested
    @DisplayName("Stock state")
    class State {

        @ParameterizedTest(name = "balance {0}, minimum {1} -> {2}")
        @CsvSource({
                "100, 10, OK",
                "10,  10, AT_MINIMUM",
                "5,   10, AT_MINIMUM",
                "0,   10, OUT_OF_STOCK",
                "-1,  10, NEGATIVE",
                // no minimum set: low cannot apply, or every item would be low
                "1,   0,  OK",
        })
        void readsTheSameLevelAsTheSalesAlert(double balance, double miniQuantity, StockLevel expected) {
            assertEquals(expected, row(balance, 1, 1, miniQuantity).level());
        }
    }

    /**
     * The filter is the reason the header can claim a number of low-stock items. If
     * it drew the line anywhere other than where {@code StockLevel} draws it, the
     * count in the bar and the warning the sales screen raises would disagree.
     */
    @Nested
    @DisplayName("Stock filter")
    class Filters {

        @Test
        @DisplayName("ALL narrows nothing and writes no condition")
        void allNarrowsNothing() {
            assertFalse(StockFilter.ALL.narrows());
            assertEquals("", StockFilter.ALL.condition("bal"));
            assertTrue(StockFilter.ALL.matches(row(0, 1, 1, 0)));
            assertTrue(StockFilter.ALL.matches(row(-5, 1, 1, 0)));
        }

        @ParameterizedTest
        @EnumSource(value = StockFilter.class, names = "ALL", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("every other filter writes a condition against the balance passed in")
        void narrowingFiltersUseTheBalanceExpression(StockFilter filter) {
            String condition = filter.condition("THE_BALANCE");

            assertTrue(filter.narrows());
            assertTrue(condition.contains("THE_BALANCE"), () -> filter + " ignored the balance: " + condition);
            assertFalse(condition.contains("%"), () -> filter + " left a placeholder unfilled: " + condition);
        }

        @ParameterizedTest
        @EnumSource(value = StockFilter.class, names = {"LOW", "OUT_OF_STOCK", "NEGATIVE"})
        @DisplayName("a filter that names one state matches exactly that state")
        void singleStateFiltersAgreeWithStockLevel(StockFilter filter) {
            StockLevel paired = filter.agreesWithStockLevel();
            assertNotNull(paired);

            for (StockLevel level : StockLevel.values()) {
                assertEquals(level == paired, filter.matches(level),
                        () -> filter + " and " + level + " disagree");
            }
        }

        @Test
        @DisplayName("IN_STOCK covers anything left on the shelf, low or not")
        void inStockCoversLowAsWell() {
            assertTrue(StockFilter.IN_STOCK.matches(StockLevel.OK));
            assertTrue(StockFilter.IN_STOCK.matches(StockLevel.AT_MINIMUM));
            assertFalse(StockFilter.IN_STOCK.matches(StockLevel.OUT_OF_STOCK));
            assertFalse(StockFilter.IN_STOCK.matches(StockLevel.NEGATIVE));
        }

        @Test
        @DisplayName("an item exactly at its minimum is low, and an item with no minimum is not")
        void theBoundaryThatGoesWrongByOne() {
            assertTrue(StockFilter.LOW.matches(row(10, 1, 1, 10)));
            assertFalse(StockFilter.LOW.matches(row(10, 1, 1, 0)));
            assertFalse(StockFilter.LOW.matches(row(11, 1, 1, 10)));
        }

        @Test
        @DisplayName("every filter has a title for the picker")
        void everyFilterIsNamed() {
            for (StockFilter filter : StockFilter.values()) {
                assertFalse(filter.title().isBlank(), () -> filter + " has no title");
            }
        }
    }
}
