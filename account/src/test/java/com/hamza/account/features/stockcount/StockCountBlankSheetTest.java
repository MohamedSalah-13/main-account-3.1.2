package com.hamza.account.features.stockcount;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The paper a count starts from - {@link StockCountBlankSheet} and its layout. */
class StockCountBlankSheetTest {

    private static DocumentPdfPage page(List<StockCountBlankSheet.Row> rows) {
        return StockCountBlankSheetLayout.of(new StockCountBlankSheet("المخزن الرئيسي", rows),
                InvoicePrintDocument.Letterhead.EMPTY, key -> key, "");
    }

    @Test
    @DisplayName("every item is a numbered row with the count left empty to write in")
    void rowsAreNumberedWithAnEmptyCount() {
        DocumentPdfPage page = page(List.of(
                new StockCountBlankSheet.Row("1001", "زيت", "زيوت", "قطعة"),
                new StockCountBlankSheet.Row("1002", "سكر", "بقالة", "كيلو")));

        assertEquals(2, page.rows().size());
        assertEquals(List.of("1", "زيت", "1001", "زيوت", "قطعة", ""), Arrays.asList(page.rows().getFirst()));
        assertEquals("2", page.rows().get(1)[0]);
        assertEquals("item.stockcount.column.counted", page.headers()[page.headers().length - 1]);
    }

    /**
     * A blind count is the only one whose differences mean anything: a counter who can read what the
     * system expects writes that down.
     */
    @Test
    @DisplayName("the book quantity is not on the paper")
    void noBookQuantity() {
        DocumentPdfPage page = page(List.of(new StockCountBlankSheet.Row("1001", "زيت", "زيوت", "قطعة")));

        assertFalse(Arrays.asList(page.headers()).contains("item.stockcount.column.system"));
        assertFalse(Arrays.asList(page.headers()).contains("item.stockcount.column.difference"));
    }

    /** The page writes its second column as text, aligned to its reading edge: the item's name goes there. */
    @Test
    @DisplayName("the item's name is the second column, and each header has a width")
    void theNameIsTheTextColumn() {
        DocumentPdfPage page = page(List.of());

        assertEquals("item.stockcount.column.item", page.headers()[1]);
        assertEquals(page.headers().length, page.columnWidths().length);
    }

    @Test
    @DisplayName("items in use only, in the order the shelves are walked")
    void itemsInUseInShelfOrder() {
        String sql = StockCountBlankSheet.SQL.replaceAll("\\s+", " ");

        assertTrue(sql.contains("WHERE ist.stock_id = ? AND i.item_active = 1"), sql);
        assertTrue(sql.contains("ORDER BY sg.name, i.nameItem, i.id"), sql);
        assertEquals(1, sql.chars().filter(c -> c == '?').count());
    }
}
