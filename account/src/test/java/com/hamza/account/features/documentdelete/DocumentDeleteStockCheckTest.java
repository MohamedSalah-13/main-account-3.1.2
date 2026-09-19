package com.hamza.account.features.documentdelete;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDeleteStockCheckTest {

    private static DocumentDeleteStockCheck.StockLine line(double current, double removed) {
        return new DocumentDeleteStockCheck.StockLine("صنف", "الرئيسي", current, removed);
    }

    @Test
    void saysNothingWhenTheShelfStillCoversIt() {
        assertTrue(DocumentDeleteStockCheck.shortfalls(List.of(line(10, 4))).isEmpty());
    }

    @Test
    void reportsWhatWouldGoBelowZero() {
        // Bought 10, sold 7, and the purchase is now being deleted: 3 on the shelf less 10.
        List<DocumentDeleteStockCheck.Shortfall> found =
                DocumentDeleteStockCheck.shortfalls(List.of(line(3, 10)));

        assertEquals(1, found.size());
        assertEquals(-7.0, found.get(0).remainingBase());
        assertEquals("صنف", found.get(0).itemName());
        assertEquals("الرئيسي", found.get(0).stockName());
    }

    @Test
    void exactlyZeroIsNotAShortfall() {
        assertTrue(DocumentDeleteStockCheck.shortfalls(List.of(line(10, 10))).isEmpty());
    }

    @Test
    void aRoundingCrumbIsNotAShortfall() {
        assertTrue(DocumentDeleteStockCheck.shortfalls(
                List.of(line(10, 10.0000001))).isEmpty());
    }

    @Test
    void anItemAlreadyNegativeIsReportedForWhatItWouldBecome() {
        // The shop sells before entering the supplier's bill, so the balance is below zero
        // already - the warning still tells the truth about where it lands.
        assertEquals(-12.0,
                DocumentDeleteStockCheck.shortfalls(List.of(line(-2, 10))).get(0).remainingBase());
    }

    @Test
    void everyItemThatWouldGoNegativeIsListed() {
        List<DocumentDeleteStockCheck.Shortfall> found = DocumentDeleteStockCheck.shortfalls(
                List.of(line(1, 5), line(100, 1), line(0, 2)));

        assertEquals(2, found.size());
    }

    @Test
    void aMissingRowIsSkippedRatherThanThrown() {
        assertTrue(DocumentDeleteStockCheck.shortfalls(Arrays.asList((DocumentDeleteStockCheck.StockLine) null))
                .isEmpty());
    }
}
