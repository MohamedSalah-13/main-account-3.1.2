package com.hamza.account.features.stockcount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One line per item on a count sheet, and what a scan does to it - without a screen or a database. */
class StockCountLinesTest {

    private static final int OIL = 7;
    private static final int SUGAR = 9;
    private static final int PIECE = 1;
    private static final int CARTON = 2;
    private static final double BOOK = 30;

    @Test
    @DisplayName("a new item is a line of one, at the top")
    void aNewItemIsALineOfOne() {
        List<StockCountLine> sheet = new ArrayList<>(List.of(line(SUGAR, PIECE, 1)));

        StockCountLine landed = StockCountLines.scan(sheet, line(OIL, PIECE, 1), PIECE, "piece");

        assertEquals(2, sheet.size());
        assertSame(landed, sheet.getFirst());
        assertEquals(1, landed.getCountedQuantity());
    }

    @Test
    @DisplayName("the same item in the same unit counts one more of that unit")
    void theSameUnitCountsOneMore() {
        List<StockCountLine> sheet = new ArrayList<>();
        StockCountLines.scan(sheet, line(OIL, CARTON, 12), PIECE, "piece");
        StockCountLines.scan(sheet, line(OIL, CARTON, 12), PIECE, "piece");

        assertEquals(1, sheet.size());
        assertEquals(2, sheet.getFirst().getCountedQuantity(), "two cartons");
        assertEquals(CARTON, sheet.getFirst().getUnitId());
    }

    /**
     * The defect this class exists for: two lines of one item each carried the whole book, and the
     * post took it off twice. Two cartons of twelve and a piece are one line of 25 pieces, against
     * one book of 30.
     */
    @Test
    @DisplayName("a second unit of an item restates its one line in the base unit")
    void aSecondUnitRestatesTheLine() {
        List<StockCountLine> sheet = new ArrayList<>();
        StockCountLines.scan(sheet, line(OIL, CARTON, 12), PIECE, "piece");
        StockCountLines.scan(sheet, line(OIL, CARTON, 12), PIECE, "piece");

        StockCountLine landed = StockCountLines.scan(sheet, line(OIL, PIECE, 1), PIECE, "piece");

        assertEquals(1, sheet.size(), "one item, one line");
        assertSame(landed, sheet.getFirst());
        assertEquals(PIECE, landed.getUnitId());
        assertEquals(1, landed.getTypeValue());
        assertEquals(25, landed.getCountedQuantity());
        assertEquals(BOOK, landed.getSystemQuantity(), "the book is taken once, from the first scan");
        assertEquals(-5, landed.difference());
    }

    @Test
    @DisplayName("a carton scanned onto a line of pieces adds its twelve")
    void aCartonOntoPiecesAddsItsFactor() {
        List<StockCountLine> sheet = new ArrayList<>();
        StockCountLines.scan(sheet, line(OIL, PIECE, 1), PIECE, "piece");

        StockCountLine landed = StockCountLines.scan(sheet, line(OIL, CARTON, 12), PIECE, "piece");

        assertEquals(1, sheet.size());
        assertEquals(13, landed.getCountedQuantity());
        assertEquals(PIECE, landed.getUnitId());
    }

    @Test
    @DisplayName("a sheet naming one item twice is found, and one naming each once is not")
    void aRepeatedItemIsFound() {
        List<StockCountLine> twice = List.of(line(OIL, CARTON, 12), line(SUGAR, PIECE, 1), line(OIL, PIECE, 1));

        assertEquals(OIL, StockCountLines.repeatedItem(twice).orElseThrow().getItemId());
        assertTrue(StockCountLines.repeatedItem(List.of(line(OIL, PIECE, 1), line(SUGAR, PIECE, 1))).isEmpty());
    }

    private static StockCountLine line(int itemId, int unitId, double factor) {
        return new StockCountLine(0, itemId, "item " + itemId, "code", unitId, unitId == PIECE ? "piece" : "carton",
                factor, BOOK, 0);
    }
}
