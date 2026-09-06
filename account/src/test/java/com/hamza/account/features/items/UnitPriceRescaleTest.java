package com.hamza.account.features.items;

import com.hamza.account.model.domain.ItemsUnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money side of changing what a unit holds.
 * <p>
 * The stock side needs no test here and never breaks: an invoice line stores the factor
 * it used, so a factor changing later cannot rewrite a balance. It is exactly that safety
 * that hides this - the quantities stay right while the price silently covers twice the
 * goods.
 */
class UnitPriceRescaleTest {

    private static ItemsUnitsModel row(double factor, double buy, double sel) {
        var row = new ItemsUnitsModel();
        row.setQuantityForUnit(factor);
        row.setBuyPrice(buy);
        row.setSelPrice(sel);
        return row;
    }

    @Nested
    @DisplayName("when there is something to ask about")
    class Needed {

        @Test
        @DisplayName("a priced unit whose factor grew")
        void pricedUnitGrowing() {
            assertTrue(UnitPriceRescale.of(row(6, 60, 100), 12).isNeeded());
        }

        @Test
        @DisplayName("a priced unit whose factor shrank")
        void pricedUnitShrinking() {
            assertTrue(UnitPriceRescale.of(row(12, 60, 100), 6).isNeeded());
        }

        @Test
        @DisplayName("a unit priced from the item is left alone - it follows the factor itself")
        void unpricedUnitNeedsNothing() {
            assertFalse(UnitPriceRescale.of(row(6, 0, 0), 12).isNeeded());
        }

        @Test
        @DisplayName("the same factor typed again is not a change")
        void sameFactor() {
            assertFalse(UnitPriceRescale.of(row(12, 60, 100), 12).isNeeded());
        }

        @Test
        @DisplayName("a row that never had a factor cannot give a ratio")
        void noOldFactor() {
            assertFalse(UnitPriceRescale.of(row(0, 60, 100), 12).isNeeded());
        }

        @Test
        @DisplayName("one price of the four is enough")
        void onlyOnePriceSet() {
            var row = new ItemsUnitsModel();
            row.setQuantityForUnit(6);
            row.setSelPrice3(90);
            assertTrue(UnitPriceRescale.of(row, 12).isNeeded());
        }
    }

    @Nested
    @DisplayName("the rescaled figures")
    class Figures {

        @Test
        @DisplayName("doubling the factor doubles the prices")
        void doubling() {
            var rescale = UnitPriceRescale.of(row(6, 60, 100), 12);
            assertEquals(2, rescale.ratio());
            assertEquals(120, rescale.newBuyPrice());
            assertEquals(200, rescale.newSelPrice());
        }

        @Test
        @DisplayName("halving the factor halves them")
        void halving() {
            var rescale = UnitPriceRescale.of(row(12, 60, 100), 6);
            assertEquals(30, rescale.newBuyPrice());
            assertEquals(50, rescale.newSelPrice());
        }

        @Test
        @DisplayName("a price is money, so it is rounded to two places")
        void rounding() {
            // 100 over three, then doubled - 66.67 rather than 66.66666666666667
            var rescale = UnitPriceRescale.of(row(3, 0, 100), 2);
            assertEquals(66.67, rescale.newSelPrice());
        }

        @Test
        @DisplayName("a price of zero stays zero, so the unit keeps following the item")
        void zeroStaysZero() {
            var rescale = UnitPriceRescale.of(row(6, 0, 100), 12);
            assertEquals(0, rescale.newBuyPrice());
            assertEquals(200, rescale.newSelPrice());
        }
    }

    @Nested
    @DisplayName("applying it")
    class Applying {

        @Test
        @DisplayName("writes every price that was set")
        void writesPrices() {
            var row = row(6, 60, 100);
            row.setSelPrice2(90);
            row.setSelPrice3(80);

            UnitPriceRescale.of(row, 12).applyTo(row);

            assertEquals(120, row.getBuyPrice());
            assertEquals(200, row.getSelPrice());
            assertEquals(180, row.getSelPrice2());
            assertEquals(160, row.getSelPrice3());
        }

        @Test
        @DisplayName("does not touch the factor - accepting the new one is not the same decision")
        void leavesTheFactorToTheCaller() {
            var row = row(6, 60, 100);
            UnitPriceRescale.of(row, 12).applyTo(row);
            assertEquals(6, row.getQuantityForUnit());
        }

        @Test
        @DisplayName("a rescale nobody needed changes nothing")
        void unneededChangesNothing() {
            var row = row(6, 0, 0);
            UnitPriceRescale.of(row, 12).applyTo(row);
            assertEquals(0, row.getBuyPrice());
            assertEquals(0, row.getSelPrice());
        }

        @Test
        @DisplayName("a null row is not a crash")
        void nullRow() {
            var rescale = UnitPriceRescale.of(null, 12);
            assertFalse(rescale.isNeeded());
            rescale.applyTo(null);
        }
    }
}
