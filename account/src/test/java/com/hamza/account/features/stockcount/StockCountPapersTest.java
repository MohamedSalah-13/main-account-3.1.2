package com.hamza.account.features.stockcount;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A count's record and the variance report, with labels left as their keys - no bundle, no toolkit. */
class StockCountPapersTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("a posted count's record says what it is, where, when it was posted and by whom")
    void thePostedRecord() {
        DocumentPdfPage page = StockCountSheetLayout.of(document(StockCountStatus.POSTED), null, key -> key, "");

        assertEquals("item.stockcount.sheet.title", page.title());
        assertEquals(List.of("12", "2026-09-21"), values(page.identity()));
        assertEquals(List.of("invoice.stock", "item.stockcount.sheet.status", "item.stockcount.sheet.posted.at",
                "stocks.transfer.slip.entered.by"), labels(page.details()));
        assertEquals("item.stockcount.status.posted", page.details().get(1).value(),
                "the status is written, so a draft cannot be filed as a correction that was made");
        assertEquals("item.stockcount.sheet.signature", page.signatureLabel());
        assertEquals("monthly", page.notes());
    }

    @Test
    @DisplayName("a draft's record says it is a draft and has no posting date")
    void theDraftRecord() {
        DocumentPdfPage page = StockCountSheetLayout.of(document(StockCountStatus.DRAFT), null, key -> key, "");

        assertEquals("item.stockcount.status.draft", page.details().get(1).value());
        assertFalse(labels(page.details()).contains("item.stockcount.sheet.posted.at"));
    }

    /** The screen's columns: the book in base units, the count in its own unit, the difference in base units. */
    @Test
    @DisplayName("every line is numbered with its book, its count and its difference, never summed")
    void theLines() {
        DocumentPdfPage page = StockCountSheetLayout.of(document(StockCountStatus.POSTED), null, key -> key, "");

        assertEquals(page.headers().length, page.columnWidths().length);
        assertArrayEquals(new String[]{"1", "6221", "juice", "carton", "30", "2", "-6"}, page.rows().get(0));
        assertArrayEquals(new String[]{"2", "7000", "soap", "piece", "10", "10", "0"}, page.rows().get(1));
        assertNull(page.totals(), "a total of juice and soap is not a quantity of anything");
        assertEquals(List.of("2", "1"), values(page.summary()), "lines, and lines with a difference");
        assertTrue(page.summary().get(1).emphasised());
    }

    @Test
    @DisplayName("the variance report is one row per item, with a header, a width and a cell per column")
    void theVarianceReport() {
        StockCountVarianceRow row = new StockCountVarianceRow(5, "6221", "juice", "piece", 3, 1, 7, -6);

        String[] headers = StockCountVarianceReport.headers(key -> key);

        assertEquals(7, headers.length);
        assertEquals(headers.length, StockCountVarianceReport.widths().length);
        assertArrayEquals(new String[]{"6221", "juice", "piece", "3", "1", "7", "-6"}, StockCountVarianceReport.row(row));
    }

    @Test
    @DisplayName("the variance spreadsheet is the paper's columns and rows")
    void theVarianceSpreadsheet() {
        StockCountVarianceRow row = new StockCountVarianceRow(5, null, "juice", null, 1, 0, 2, -2);
        WriteExcelInterface<String[]> sheet = StockCountVarianceReport.spreadsheet("variance", key -> key, List.of(row));

        assertArrayEquals(StockCountVarianceReport.headers(key -> key), sheet.columnHeader());
        assertArrayEquals(new String[]{"", "juice", "", "1", "0", "2", "-2"}, sheet.dataRow(sheet.itemsList().getFirst()));
    }

    private static StockCountDocument document(StockCountStatus status) {
        StockCountSummary header = new StockCountSummary(12, DAY, 1, "main", status, "monthly",
                status.isPosted() ? LocalDateTime.of(2026, 9, 21, 10, 0) : null, "soha", 2, 1);
        return new StockCountDocument(header, List.of(
                new StockCountLine(0, 5, "juice", "6221", 2, "carton", 12, 30, 2),
                new StockCountLine(0, 6, "soap", "7000", 1, "piece", 1, 10, 10)));
    }

    private static List<String> values(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::value).toList();
    }

    private static List<String> labels(List<DocumentPdfPage.Field> fields) {
        return fields.stream().map(DocumentPdfPage.Field::label).toList();
    }
}
