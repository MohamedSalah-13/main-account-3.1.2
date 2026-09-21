package com.hamza.account.features.stocktransfer;

import com.hamza.account.features.export.DocumentPdfPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a transfer slip says, with the labels left as their keys - so a wrong label on a value shows
 * as a wrong key, without a bundle or a toolkit.
 */
class StockTransferSlipLayoutTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("the slip names itself, its number and date, and where the goods went from and to")
    void theHeader() {
        DocumentPdfPage page = StockTransferSlipLayout.of(slip("soha", "for the winter fair"), null, key -> key, "");

        assertEquals("stocks.transfer.slip.title", page.title());
        assertEquals(List.of("17", "2026-09-21"), values(page.identity()));
        assertEquals(List.of("stocks.transfer.from", "stocks.transfer.to", "stocks.transfer.slip.entered.by"),
                labels(page.details()));
        assertEquals(List.of("main", "branch", "soha"), values(page.details()));
        assertEquals("for the winter fair", page.notes());
        assertEquals("stocks.transfer.slip.signature", page.signatureLabel());
        assertEquals("", page.footer(), "no printing time asked for, no footer");
    }

    /**
     * The quantities are the ones entered, each in its own unit: the receiver counts two cartons
     * and three pieces, and a slip reading "27" is a number nobody on the van can check.
     */
    @Test
    @DisplayName("every line is numbered and printed in the unit it was entered in, never summed")
    void theLinesAsEntered() {
        DocumentPdfPage page = StockTransferSlipLayout.of(slip("soha", null), null, key -> key, "");

        assertEquals(2, page.rows().size());
        assertArrayEquals(new String[]{"1", "6221", "juice", "carton", "2"}, page.rows().get(0));
        assertArrayEquals(new String[]{"2", "6221", "juice", "piece", "3"}, page.rows().get(1));
        assertEquals(null, page.totals(), "a total of cartons and pieces is not a quantity of anything");
        assertEquals(List.of("2"), values(page.summary()), "how many lines - what the receiver checks first");
        assertTrue(page.summary().getFirst().emphasised());
    }

    @Test
    @DisplayName("each header has a width, and the page is the document path's, not a report's")
    void oneWidthPerHeader() {
        DocumentPdfPage page = StockTransferSlipLayout.of(slip("soha", null), null, key -> key, "");

        assertEquals(page.headers().length, page.columnWidths().length);
        assertEquals(5, page.headers().length);
    }

    @Test
    @DisplayName("nobody to name and nothing written leave no empty line and no empty note")
    void absentValuesLeaveNoGap() {
        DocumentPdfPage page = StockTransferSlipLayout.of(slip(null, null), null, key -> key, "2026-09-21 10:00:00");

        assertFalse(labels(page.details()).contains("stocks.transfer.slip.entered.by"));
        assertEquals("", page.notes());
        assertEquals("invoice.pdf.printed.at: 2026-09-21 10:00:00", page.footer());
    }

    private static StockTransferSlip slip(String enteredBy, String notes) {
        StockTransferSummary header = new StockTransferSummary(17, DAY, 1, "main", 2, "branch", 2, notes, enteredBy);
        return new StockTransferSlip(header, List.of(
                new StockTransferLineRow(5, "6221", "juice", "carton", 2),
                new StockTransferLineRow(5, "6221", "juice", "piece", 3)));
    }

    private static List<String> values(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::value).toList();
    }

    private static List<String> labels(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::label).toList();
    }
}
