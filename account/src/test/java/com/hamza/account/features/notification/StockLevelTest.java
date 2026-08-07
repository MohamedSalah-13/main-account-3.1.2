package com.hamza.account.features.notification;

import com.hamza.controlsfx.notifications.NotificationSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundaries of the sale-time stock warning. Exactly at the minimum and
 * exactly at zero are the two the user asked about by name, and both are the kind
 * that end up one out.
 */
class StockLevelTest {

    @Nested
    @DisplayName("Classification")
    class Classification {

        @ParameterizedTest(name = "balance {0}, minimum {1} -> {2}")
        @CsvSource({
                // comfortably in stock
                "100, 10, OK",
                "11,  10, OK",
                // at or below the minimum, still in stock
                "10,  10, AT_MINIMUM",
                "9,   10, AT_MINIMUM",
                "0.5, 10, AT_MINIMUM",
                // nothing left
                "0,   10, OUT_OF_STOCK",
                "0,    0, OUT_OF_STOCK",
                // sold past what exists
                "-1,  10, NEGATIVE",
                "-0.5, 0, NEGATIVE",
        })
        void classifies(double balance, double miniQuantity, StockLevel expected) {
            assertEquals(expected, StockLevel.of(balance, miniQuantity));
        }

        @Test
        @DisplayName("treats a minimum of zero as 'no minimum set' rather than as a threshold")
        void unsetMinimumDoesNotMakeEveryItemLow() {
            assertEquals(StockLevel.OK, StockLevel.of(1, 0),
                    "an item with no minimum configured must not report itself as low");
            assertEquals(StockLevel.OK, StockLevel.of(0.01, 0));
        }

        @Test
        @DisplayName("a negative minimum is ignored the same way")
        void negativeMinimumIsIgnored() {
            assertEquals(StockLevel.OK, StockLevel.of(5, -3));
        }

        @Test
        @DisplayName("running out outranks being below the minimum")
        void zeroIsReportedAsOutOfStockNotAsLow() {
            assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.of(0, 100));
        }
    }

    @Nested
    @DisplayName("Severity")
    class Severity {

        @Test
        @DisplayName("says nothing when the level is fine")
        void okIsNotReported() {
            assertFalse(StockLevel.OK.isWorthReporting());
        }

        @Test
        @DisplayName("reports the three problem levels")
        void problemsAreReported() {
            assertTrue(StockLevel.AT_MINIMUM.isWorthReporting());
            assertTrue(StockLevel.OUT_OF_STOCK.isWorthReporting());
            assertTrue(StockLevel.NEGATIVE.isWorthReporting());
        }

        @Test
        @DisplayName("treats a negative balance as louder than an empty one")
        void negativeIsAnErrorNotAWarning() {
            // Out of stock is a normal state to reach; a negative balance means the
            // recorded stock no longer matches what was sold.
            assertEquals(NotificationSeverity.WARNING, StockLevel.OUT_OF_STOCK.severity());
            assertEquals(NotificationSeverity.ERROR, StockLevel.NEGATIVE.severity());
            assertTrue(StockLevel.NEGATIVE.severity().atLeast(StockLevel.OUT_OF_STOCK.severity()));
        }
    }
}
