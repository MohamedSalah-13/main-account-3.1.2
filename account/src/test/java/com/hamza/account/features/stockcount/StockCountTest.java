package com.hamza.account.features.stockcount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic a stock count posts, and the rule about when it may post it.
 * <p>
 * Both are worth pinning down without a database. The difference is what moves every
 * balance in the shop, and it is the one place a unit factor can silently turn four
 * cartons into four pieces; the status is what stops a sheet being posted twice.
 */
class StockCountTest {

    private static StockCountLine line(double typeValue, double systemQuantity, double counted) {
        return new StockCountLine(0, 1, "سكر", "123", 2, "كرتونة",
                typeValue, systemQuantity, counted);
    }

    @Nested
    @DisplayName("Difference")
    class Difference {

        @ParameterizedTest(name = "factor {0}, system {1}, counted {2} -> {3}")
        @CsvSource({
                // counted in the base unit
                "1, 100, 100,   0",
                "1, 100, 90,  -10",
                "1, 100, 130,  30",
                // counted in cartons of twelve: four cartons is forty-eight pieces
                "12, 48, 4,     0",
                "12, 48, 3,   -12",
                "12, 48, 5,    12",
                // nothing on the books and something on the shelf
                "1,  0,  7,     7",
                // on the books and nothing on the shelf
                "1,  7,  0,    -7",
        })
        void convertsBeforeComparing(double typeValue, double system, double counted, double expected) {
            assertEquals(expected, line(typeValue, system, counted).difference());
        }

        @Test
        @DisplayName("a count that matches the books moves nothing")
        void matchingCountHasNoDifference() {
            assertFalse(line(1, 25, 25).hasDifference());
            assertTrue(line(1, 25, 24).hasDifference());
        }

        @Test
        @DisplayName("a negative system balance is corrected up to what was found")
        void correctsANegativeBalance() {
            // Sold past what the stock said existed, then three found on the shelf.
            assertEquals(5, line(1, -2, 3).difference());
        }

        /**
         * The case that reads as a bug and is not. The books say -10, fifteen are on the
         * shelf, and the adjustment is +25 rather than the +5 the two numbers suggest -
         * because +5 would leave the item at -5 after a count that found fifteen of them.
         */
        @Test
        @DisplayName("the adjustment closes the whole gap, so posting lands on what was counted")
        void postingLandsOnWhatWasCounted() {
            StockCountLine line = line(1, -10, 15);

            assertEquals(25, line.difference());
            assertEquals(15, line.resultingBalance());
            assertEquals(15, line.getSystemQuantity() + line.difference(),
                    "the balance after posting must be exactly what was counted");
        }

        @ParameterizedTest(name = "system {0}, counted {1} ends at {1}")
        @CsvSource({"100, 90", "100, 130", "-10, 15", "-10, 0", "0, 7", "-4, -4"})
        void alwaysEndsAtTheCountedQuantity(double system, double counted) {
            StockCountLine line = line(1, system, counted);

            assertEquals(counted, line.getSystemQuantity() + line.difference());
            assertEquals(counted, line.resultingBalance());
        }

        @Test
        @DisplayName("the resulting balance is in base units, not the counted unit")
        void resultingBalanceIsInBaseUnits() {
            // Four cartons of twelve found where the books said forty-eight pieces.
            assertEquals(48, line(12, 48, 4).resultingBalance());
        }

        @Test
        @DisplayName("negative books are flagged, zero and positive are not")
        void flagsNegativeBooks() {
            assertTrue(line(1, -10, 15).hasNegativeSystemBalance());
            assertFalse(line(1, 0, 15).hasNegativeSystemBalance());
            assertFalse(line(1, 10, 15).hasNegativeSystemBalance());
        }

        @ParameterizedTest(name = "factor {0} is treated as 1")
        @ValueSource(doubles = {0, -1, -12})
        void refusesAFactorThatWouldZeroOrReverseTheCount(double typeValue) {
            // The same guard ItemUnits.factor applies: a zero factor would post the
            // whole balance as missing, and a negative one would invert the count.
            StockCountLine line = line(typeValue, 10, 4);

            assertEquals(1, line.getTypeValue());
            assertEquals(-6, line.difference());
        }

        @Test
        @DisplayName("counted quantity is in the counted unit, converted only for comparison")
        void countedStaysInItsOwnUnit() {
            StockCountLine line = line(12, 48, 4);

            assertEquals(4, line.getCountedQuantity());
            assertEquals(48, line.countedInBaseUnits());
        }
    }

    @Nested
    @DisplayName("Sheet")
    class Sheet {

        private static StockCount sheetOf(StockCountLine... lines) {
            StockCount count = new StockCount();
            count.setLines(List.of(lines));
            return count;
        }

        @Test
        @DisplayName("only the lines that differ are counted as moving anything")
        void countsOnlyTheLinesThatDiffer() {
            StockCount count = sheetOf(line(1, 10, 10), line(1, 10, 8), line(1, 5, 9));

            assertEquals(2, count.linesWithDifference().size());
        }

        @Test
        @DisplayName("the net is a surplus and a shortage cancelling out, not their sizes")
        void netIsSigned() {
            StockCount count = sheetOf(line(1, 10, 14), line(1, 20, 16));

            assertEquals(0, count.netDifference());
        }

        @Test
        @DisplayName("a new sheet is a draft, and a draft may be changed")
        void aNewSheetIsAnEditableDraft() {
            StockCount count = new StockCount();

            assertTrue(count.isNew());
            assertFalse(count.isPosted());
            assertTrue(count.isEditable());
        }

        @Test
        @DisplayName("a posted sheet is closed to changes")
        void aPostedSheetIsReadOnly() {
            StockCount count = new StockCount();
            count.setStatus(StockCountStatus.POSTED);

            assertTrue(count.isPosted());
            assertFalse(count.isEditable());
        }

        @Test
        @DisplayName("setting the lines replaces them rather than appending")
        void linesAreReplaced() {
            StockCount count = sheetOf(line(1, 1, 1), line(1, 2, 2));

            count.setLines(List.of(line(1, 3, 3)));

            assertEquals(1, count.getLines().size());
        }
    }

    @Nested
    @DisplayName("Status")
    class Status {

        @Test
        @DisplayName("reads the value stored in the row")
        void readsStoredValues() {
            assertEquals(StockCountStatus.DRAFT, StockCountStatus.of("DRAFT"));
            assertEquals(StockCountStatus.POSTED, StockCountStatus.of("POSTED"));
            assertEquals(StockCountStatus.POSTED, StockCountStatus.of("posted"));
        }

        @Test
        @DisplayName("anything unrecognised is a draft, so an unreadable row cannot move stock")
        void unknownIsADraft() {
            assertEquals(StockCountStatus.DRAFT, StockCountStatus.of(null));
            assertEquals(StockCountStatus.DRAFT, StockCountStatus.of(""));
            assertEquals(StockCountStatus.DRAFT, StockCountStatus.of("ARCHIVED"));
        }

        @Test
        @DisplayName("only POSTED reports itself as posted")
        void onlyPostedIsPosted() {
            assertFalse(StockCountStatus.DRAFT.isPosted());
            assertTrue(StockCountStatus.POSTED.isPosted());
        }

        @Test
        @DisplayName("every status is named for the screen")
        void everyStatusIsNamed() {
            for (StockCountStatus status : StockCountStatus.values()) {
                assertFalse(status.title().isBlank(), () -> status + " has no title");
            }
        }
    }
}
