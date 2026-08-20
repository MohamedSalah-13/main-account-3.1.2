package com.hamza.account.features.itemmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the user is shown before they commit, and what the log records afterwards.
 * <p>
 * Both are this record, and the number that matters most to a user - "how many
 * operations were carried out on this item" - is the four documents, not the total: a
 * unit row and a barcode are not operations, and counting them makes the sentence on
 * screen wrong in a way nobody would question.
 */
class ItemMergePreviewTest {

    private static MergeItem item(int id) {
        return new MergeItem(id, "صنف " + id, "100" + id, 1, false, BigDecimal.ZERO);
    }

    private static ItemMergePreview preview(Map<String, Integer> rows, int locked) {
        return new ItemMergePreview(item(1), item(2), rows, locked);
    }

    private static Map<String, Integer> rows(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("the documents are counted apart from everything else")
    void documentsAreNotTheTotal() {
        ItemMergePreview preview = preview(rows(
                "sales.num", 124,
                "sales_re.item_id", 4,
                "purchase.num", 31,
                "purchase_re.item_id", 2,
                "items_units.items_id", 3,
                "item_barcodes.item_id", 5), 42);

        assertEquals(161, preview.documentLines());
        assertEquals(169, preview.totalRows());
        assertEquals(42, preview.lockedPeriodLines());
    }

    @Test
    @DisplayName("a reference with no rows counts as none rather than failing")
    void missingReferenceIsZero() {
        ItemMergePreview preview = preview(rows("sales.num", 3), 0);
        assertEquals(3, preview.rowsFor(ItemReferenceRegistry.SALES));
        assertEquals(0, preview.rowsFor(ItemReferenceRegistry.STOCK_MOVEMENTS));
    }

    /**
     * An item nobody ever sold is still worth merging - it is the duplicate that should
     * not have existed - so an empty preview is a fact to show, not a refusal.
     */
    @Test
    @DisplayName("an item with no history previews as empty")
    void emptyIsAllowed() {
        assertTrue(preview(rows(), 0).isEmpty());
        assertEquals(0, preview(rows(), 0).totalRows());
    }

    /**
     * The order is the registry's, which is the order the screen lists the tables in and
     * the order the log rows are written. {@code Map.copyOf} would lose it.
     */
    @Test
    @DisplayName("the rows keep the order they were counted in")
    void orderIsKept() {
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (ItemReference reference : ItemReferenceRegistry.ALL) {
            counted.put(reference.qualified(), 1);
        }

        List<String> seen = new ArrayList<>(preview(counted, 0).rows().keySet());
        assertEquals(ItemReferenceRegistry.ALL.stream().map(ItemReference::qualified).toList(), seen);
    }

    @Test
    @DisplayName("the counts cannot be changed after the fact")
    void rowsAreACopy() {
        Map<String, Integer> mutable = rows("sales.num", 1);
        ItemMergePreview preview = preview(mutable, 0);
        mutable.put("sales.num", 99);

        assertEquals(1, preview.rowsFor(ItemReferenceRegistry.SALES));
        assertThrows(UnsupportedOperationException.class, () -> preview.rows().put("sales.num", 99));
    }
}
