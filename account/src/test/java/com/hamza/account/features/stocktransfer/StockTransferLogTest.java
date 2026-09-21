package com.hamza.account.features.stocktransfer;

import com.hamza.controlsfx.excel.WriteExcelInterface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The transfer log's columns, for the paper and the spreadsheet alike. */
class StockTransferLogTest {

    private static final StockTransferReportRow ROW = new StockTransferReportRow(17, LocalDate.of(2026, 9, 21),
            "main", "branch", "6221", "juice", "carton", 2, "for the winter fair");

    @Test
    @DisplayName("a header, a width and a cell per column - nine of each")
    void oneOfEachPerColumn() {
        String[] headers = StockTransferLog.headers(key -> key);

        assertEquals(9, headers.length);
        assertEquals(headers.length, StockTransferLog.widths().length);
        assertEquals(headers.length, StockTransferLog.row(ROW).length);
    }

    @Test
    @DisplayName("a line is written as entered, with its transfer's note beside it")
    void aLineAsEntered() {
        assertArrayEquals(new String[]{"17", "2026-09-21", "main", "branch", "6221", "juice", "carton", "2",
                "for the winter fair"}, StockTransferLog.row(ROW));
    }

    @Test
    @DisplayName("nothing written is an empty cell, never the word null")
    void absentValuesAreEmpty() {
        StockTransferReportRow bare = new StockTransferReportRow(3, LocalDate.of(2026, 9, 21), "main", "branch",
                null, "juice", null, 1, null);

        String[] cells = StockTransferLog.row(bare);

        assertEquals("", cells[4]);
        assertEquals("", cells[6]);
        assertEquals("", cells[8]);
    }

    @Test
    @DisplayName("the spreadsheet is the paper's columns and rows, not a second choice of them")
    void theSpreadsheetIsThePaper() {
        WriteExcelInterface<String[]> sheet = StockTransferLog.spreadsheet("log", key -> key, List.of(ROW));

        assertArrayEquals(StockTransferLog.headers(key -> key), sheet.columnHeader());
        assertEquals(1, sheet.itemsList().size());
        assertArrayEquals(StockTransferLog.row(ROW), sheet.dataRow(sheet.itemsList().getFirst()));
        assertEquals("log", sheet.sheetName());
    }
}
