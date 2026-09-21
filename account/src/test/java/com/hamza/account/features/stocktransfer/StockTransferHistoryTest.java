package com.hamza.account.features.stocktransfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The filter and the page of the transfer history - the decisions, apart from the screen and the database. */
class StockTransferHistoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("the history opens on the month so far, every warehouse, no text, the first page")
    void theOpeningFilter() {
        StockTransferHistoryFilter filter = StockTransferHistoryFilter.thisMonth(DAY);

        assertEquals(LocalDate.of(2026, 9, 1), filter.from());
        assertEquals(DAY, filter.to());
        assertNull(filter.stockId());
        assertNull(filter.text());
        assertEquals(0, filter.page());
        assertEquals(StockTransferHistoryFilter.PAGE_SIZE, filter.pageSize());
    }

    @Test
    @DisplayName("a reversed period, a negative page and an absurd page size are refused")
    void whatAFilterRefuses() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockTransferHistoryFilter(DAY, DAY.minusDays(1), null, null, 0, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new StockTransferHistoryFilter(DAY, DAY, null, null, -1, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new StockTransferHistoryFilter(DAY, DAY, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StockTransferHistoryFilter(DAY, DAY, null, null,
                0, StockTransferHistoryFilter.PRINT_LIMIT + 1));
        assertThrows(NullPointerException.class,
                () -> new StockTransferHistoryFilter(null, DAY, null, null, 0, 50));
    }

    /**
     * A box somebody cleared is "no text", not a search for the empty string - which, as a
     * contains-match, would still match everything, but through a subquery per transfer.
     */
    @Test
    @DisplayName("blank text is no text, and text is trimmed")
    void blankTextIsNoText() {
        assertNull(new StockTransferHistoryFilter(DAY, DAY, null, "   ", 0, 50).text());
        assertEquals("soap", new StockTransferHistoryFilter(DAY, DAY, null, "  soap ", 0, 50).text());
    }

    @Test
    @DisplayName("a page reads one row more than it shows, and paging keeps the filter")
    void pagingArithmetic() {
        StockTransferHistoryFilter third = new StockTransferHistoryFilter(DAY, DAY, 3, "soap", 0, 50).onPage(2);

        assertEquals(100, third.offset());
        assertEquals(51, third.queryLimit());
        assertEquals(3, third.stockId());
        assertEquals("soap", third.text());
    }

    @Test
    @DisplayName("printing reads the whole filtered set from its first row, not the page on screen")
    void printingIgnoresThePage() {
        StockTransferHistoryFilter print = new StockTransferHistoryFilter(DAY, DAY, 3, "soap", 4, 50).forPrint();

        assertEquals(0, print.page());
        assertEquals(StockTransferHistoryFilter.PRINT_LIMIT, print.pageSize());
        assertEquals(3, print.stockId());
        assertEquals("soap", print.text());
    }

    @Test
    @DisplayName("the extra row says there is a next page, and is not shown")
    void theExtraRowIsCut() {
        StockTransferHistoryFilter filter = new StockTransferHistoryFilter(DAY, DAY, null, null, 1, 2);

        StockTransferHistoryPage full = StockTransferHistoryPage.of(List.of(row(1), row(2), row(3)), 9, 20, filter);
        StockTransferHistoryPage last = StockTransferHistoryPage.of(List.of(row(1), row(2)), 9, 20, filter);

        assertEquals(List.of(row(1), row(2)), full.rows());
        assertTrue(full.hasNext());
        assertTrue(full.hasPrevious());
        assertFalse(last.hasNext());
        assertEquals(9, full.transfers(), "the totals are the whole set's, whatever the page holds");
        assertEquals(20, full.lines());
    }

    @Test
    @DisplayName("the first page has no previous page")
    void theFirstPageHasNoPrevious() {
        StockTransferHistoryPage first = StockTransferHistoryPage.of(List.of(row(1)), 1, 1,
                StockTransferHistoryFilter.thisMonth(DAY));

        assertFalse(first.hasPrevious());
        assertFalse(first.hasNext());
    }

    private static StockTransferSummary row(int id) {
        return new StockTransferSummary(id, DAY, 1, "main", 2, "branch", 1, null, "admin");
    }
}
