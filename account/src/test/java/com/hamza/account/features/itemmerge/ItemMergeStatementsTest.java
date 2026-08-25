package com.hamza.account.features.itemmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A golden master of every statement a merge runs.
 * <p>
 * No database, and none is needed: what separates a merge that repoints a customer's
 * history from one that destroys it is the text of these statements and the order of
 * the parameters bound to them, and both are built in memory.
 * <p>
 * The failure this guards against does not look like a failure. Swap the two parameters
 * of a move and it still runs, and hands every line of the <em>target</em> to the item
 * that is about to be deleted. Drop the {@code IGNORE} from a barcode statement and the
 * merge starts failing on shops that had the code on both items - which is most of them,
 * that being the reason for merging. Change {@code <=} to {@code <} in a locked-period
 * count and the number shown to the user is quietly wrong.
 * <p>
 * If a value below changes, the statement it pins changed too - and the migration or the
 * screen that reads the column has to change with it.
 */
class ItemMergeStatementsTest {

    /** Every placeholder is bound and nothing is bound twice. Off by one here is the merge's failure mode. */
    private static void assertTakes(int parameters, String sql) {
        assertEquals(parameters, sql.chars().filter(character -> character == '?').count(),
                "the statement does not take the number of parameters its caller binds: " + sql);
    }

    @Nested
    @DisplayName("the plain moves")
    class Moves {

        @Test
        @DisplayName("one UPDATE per reference, target first")
        void moveText() {
            assertEquals("UPDATE sales SET num = ? WHERE num = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.SALES));
            assertEquals("UPDATE sales_re SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.SALES_RETURN));
            assertEquals("UPDATE purchase SET num = ? WHERE num = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.PURCHASE));
            assertEquals("UPDATE purchase_re SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.PURCHASE_RETURN));
            assertEquals("UPDATE stock_movements SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.STOCK_MOVEMENTS));
            assertEquals("UPDATE stock_transfer_list SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.move(ItemReferenceRegistry.STOCK_TRANSFER_LINES));
        }

        @Test
        @DisplayName("one SELECT COUNT per reference")
        void countText() {
            assertEquals("SELECT COUNT(*) FROM sales WHERE num = ?",
                    ItemMergeStatements.count(ItemReferenceRegistry.SALES));
            assertEquals("SELECT COUNT(*) FROM items_units WHERE items_id = ?",
                    ItemMergeStatements.count(ItemReferenceRegistry.ITEMS_UNITS));
            assertEquals("SELECT COUNT(*) FROM items_package WHERE package_id = ?",
                    ItemMergeStatements.count(ItemReferenceRegistry.ITEMS_PACKAGE_PACKAGE));
        }

        @Test
        @DisplayName("every reference builds a statement of the arity its caller binds")
        void everyReferenceIsBindable() {
            for (ItemReference reference : ItemReferenceRegistry.ALL) {
                assertTakes(2, ItemMergeStatements.move(reference));
                assertTakes(1, ItemMergeStatements.count(reference));
            }
        }
    }

    @Nested
    @DisplayName("finding the duplicates")
    class Candidates {

        /**
         * The whole query, once. The two things that break quietly here are the group
         * expression appearing in one place and not the other - which turns the join into
         * a cross product - and the limit ending up somewhere it does nothing.
         */
        @Test
        void theCandidatesQuery() {
            assertEquals("""
                    SELECT i.id,
                           i.nameItem,
                           i.barcode,
                           i.unit_id,
                           u.unit_name,
                           i.item_has_validity,
                           i.sel_price1,
                           i.first_balance,
                           REPLACE(LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.nameItem, 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ة', 'ه'), 'ى', 'ي'))), ' ', '') AS group_key,
                           COALESCE(c.line_count, 0) AS line_count,
                           c.last_movement
                    FROM items i
                             LEFT JOIN units u ON u.unit_id = i.unit_id
                             LEFT JOIN (SELECT item_num,
                                               COUNT(*)         AS line_count,
                                               MAX(invoice_date) AS last_movement
                                        FROM card_item_view
                                        GROUP BY item_num) c ON c.item_num = i.id
                             JOIN (SELECT REPLACE(LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.nameItem, 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ة', 'ه'), 'ى', 'ي'))), ' ', '') AS k
                                   FROM items i
                                   GROUP BY k
                                   HAVING COUNT(*) > 1) g ON g.k = REPLACE(LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.nameItem, 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ة', 'ه'), 'ى', 'ي'))), ' ', '')
                    ORDER BY group_key, line_count DESC, i.id
                    LIMIT 500""", ItemMergeStatements.candidates(MergeGroupBy.NAME, 500));
        }

        /** Nothing is bound: the limit is inlined, and the DAO clamps it before it gets here. */
        @Test
        void nothingIsBound() {
            for (MergeGroupBy groupBy : MergeGroupBy.values()) {
                assertTakes(0, ItemMergeStatements.candidates(groupBy, 100));
            }
        }

        /**
         * Every grouping folds the Arabic spelling first, and the two that are not the
         * whole name are narrowed by the sub-group - otherwise "same price" is a list of
         * every item at 5 pounds and "first word" is every item starting with "كيس".
         */
        @Test
        void theGroupExpressions() {
            assertEquals("REPLACE(LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(i.nameItem,"
                         + " 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ة', 'ه'), 'ى', 'ي'))), ' ', '')",
                    ItemMergeStatements.groupKey(MergeGroupBy.NAME));

            assertEquals("CONCAT(i.sub_num, ':', SUBSTRING_INDEX(LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                         + "i.nameItem, 'أ', 'ا'), 'إ', 'ا'), 'آ', 'ا'), 'ة', 'ه'), 'ى', 'ي'))), ' ', 1))",
                    ItemMergeStatements.groupKey(MergeGroupBy.FIRST_WORD));

            assertEquals("CONCAT(i.sub_num, ':', i.sel_price1)",
                    ItemMergeStatements.groupKey(MergeGroupBy.PRICE));
        }

        /** The expression appears three times and must be the same string in all three. */
        @Test
        void theGroupKeyIsUsedConsistently() {
            for (MergeGroupBy groupBy : MergeGroupBy.values()) {
                String key = ItemMergeStatements.groupKey(groupBy);
                String sql = ItemMergeStatements.candidates(groupBy, 10);
                int occurrences = sql.split(java.util.regex.Pattern.quote(key), -1).length - 1;
                assertEquals(3, occurrences,
                        "the group expression of " + groupBy + " must appear in the SELECT, in the derived"
                        + " table and in the join condition, identically");
            }
        }
    }

    @Nested
    @DisplayName("the item")
    class Item {

        @Test
        void selectItem() {
            assertEquals("""
                    SELECT id, nameItem, barcode, unit_id, item_has_validity, first_balance
                    FROM items
                    WHERE id = ?""", ItemMergeStatements.SELECT_ITEM);
            assertTakes(1, ItemMergeStatements.SELECT_ITEM);
        }

        /** Added, not replaced - and read in Java first, MySQL refusing a subquery on the table it updates. */
        @Test
        void addFirstBalance() {
            assertEquals("UPDATE items SET first_balance = first_balance + ? WHERE id = ?",
                    ItemMergeStatements.ADD_FIRST_BALANCE);
            assertTakes(2, ItemMergeStatements.ADD_FIRST_BALANCE);
        }
    }

    @Nested
    @DisplayName("the closed period")
    class LockedPeriod {

        /**
         * On or before the locked day - {@code AccountingLock.covers} is inclusive, and a
         * count that used {@code &lt;} would under-report the last closed day.
         */
        @Test
        void lockedCountsAreInclusiveAndJoinTheirHeader() {
            assertEquals("""
                    SELECT COUNT(*) FROM sales s
                             JOIN total_sales h ON h.invoice_number = s.invoice_number
                    WHERE s.num = ? AND h.invoice_date <= ?""", ItemMergeStatements.COUNT_LOCKED_SALES);

            assertEquals("""
                    SELECT COUNT(*) FROM sales_re r
                             JOIN total_sales_re h ON h.id = r.invoice_number
                    WHERE r.item_id = ? AND h.invoice_date <= ?""", ItemMergeStatements.COUNT_LOCKED_SALES_RETURN);

            assertEquals("""
                    SELECT COUNT(*) FROM purchase p
                             JOIN total_buy h ON h.invoice_number = p.invoice_number
                    WHERE p.num = ? AND h.invoice_date <= ?""", ItemMergeStatements.COUNT_LOCKED_PURCHASE);

            assertEquals("""
                    SELECT COUNT(*) FROM purchase_re r
                             JOIN total_buy_re h ON h.id = r.invoice_number
                    WHERE r.item_id = ? AND h.invoice_date <= ?""", ItemMergeStatements.COUNT_LOCKED_PURCHASE_RETURN);

            assertEquals("""
                    SELECT COUNT(*) FROM stock_count_lines l
                             JOIN stock_count c ON c.id = l.count_id
                    WHERE l.item_id = ? AND c.count_date <= ?""", ItemMergeStatements.COUNT_LOCKED_STOCK_COUNT_LINES);
        }

        /**
         * The header of each family, in the words that family uses: the two invoices key
         * their lines by {@code invoice_number} and the two returns by the header's
         * {@code id}, which is the asymmetry {@code DocumentTableSpec} exists for.
         */
        @Test
        void eachFamilyJoinsItsOwnHeader() {
            assertTrue(ItemMergeStatements.COUNT_LOCKED_SALES.contains("h.invoice_number = s.invoice_number"));
            assertTrue(ItemMergeStatements.COUNT_LOCKED_SALES_RETURN.contains("h.id = r.invoice_number"));
            assertTrue(ItemMergeStatements.COUNT_LOCKED_PURCHASE.contains("h.invoice_number = p.invoice_number"));
            assertTrue(ItemMergeStatements.COUNT_LOCKED_PURCHASE_RETURN.contains("h.id = r.invoice_number"));
        }

        @Test
        void everyLockedCountTakesItemAndDay() {
            for (String sql : List.of(ItemMergeStatements.COUNT_LOCKED_SALES,
                    ItemMergeStatements.COUNT_LOCKED_SALES_RETURN,
                    ItemMergeStatements.COUNT_LOCKED_PURCHASE,
                    ItemMergeStatements.COUNT_LOCKED_PURCHASE_RETURN,
                    ItemMergeStatements.COUNT_LOCKED_STOCK_COUNT_LINES)) {
                assertTakes(2, sql);
            }
        }
    }

    @Nested
    @DisplayName("stock count lines")
    class StockCountLines {

        /**
         * Both halves are added. The view reads {@code counted_qty * type_value -
         * system_qty}, so summing one and not the other invents a difference that nobody
         * counted and moves the balance by it.
         */
        @Test
        void bothHalvesAreSummed() {
            assertEquals("""
                    UPDATE stock_count_lines t
                             JOIN stock_count_lines s
                               ON s.count_id = t.count_id AND s.unit_id = t.unit_id
                    SET t.system_qty  = t.system_qty  + s.system_qty,
                        t.counted_qty = t.counted_qty + s.counted_qty
                    WHERE t.item_id = ? AND s.item_id = ?""", ItemMergeStatements.SUM_STOCK_COUNT_LINES);
            assertTakes(2, ItemMergeStatements.SUM_STOCK_COUNT_LINES);
        }

        @Test
        void theSummedRowsAreRemoved() {
            assertEquals("""
                    DELETE s FROM stock_count_lines s
                             JOIN stock_count_lines t
                               ON t.count_id = s.count_id AND t.unit_id = s.unit_id AND t.item_id = ?
                    WHERE s.item_id = ?""", ItemMergeStatements.DELETE_SUMMED_STOCK_COUNT_LINES);
            assertTakes(2, ItemMergeStatements.DELETE_SUMMED_STOCK_COUNT_LINES);
        }
    }

    @Nested
    @DisplayName("stock, units and barcodes")
    class StockUnitsAndBarcodes {

        @Test
        void aRowForEveryWarehouseTheTargetLacks() {
            assertEquals("""
                    INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
                    SELECT ?, s.stock_id, 0, 0
                    FROM items_stock s
                             LEFT JOIN items_stock t ON t.item_id = ? AND t.stock_id = s.stock_id
                    WHERE s.item_id = ? AND t.id IS NULL""", ItemMergeStatements.INSERT_MISSING_ITEMS_STOCK);
            assertTakes(3, ItemMergeStatements.INSERT_MISSING_ITEMS_STOCK);
        }

        /**
         * Moved, not copied: {@code UNIQUE(items_barcode)} is global, so a copy collides
         * with the row it was copied from. And never the target's base unit, which is
         * {@code items.unit_id} and not a row here at all.
         */
        @Test
        void unitsMoveOnlyWhereTheTargetHasNone() {
            assertEquals("""
                    UPDATE items_units s
                             LEFT JOIN items_units t ON t.items_id = ? AND t.unit = s.unit
                    SET s.items_id = ?
                    WHERE s.items_id = ? AND t.id IS NULL AND s.unit <> ?""",
                    ItemMergeStatements.MOVE_ABSENT_UNITS);
            assertTakes(4, ItemMergeStatements.MOVE_ABSENT_UNITS);
        }

        /** IGNORE, because a code the target already holds is the normal case, not an error. */
        @Test
        void barcodesMoveAndDuplicatesAreIgnored() {
            assertEquals("UPDATE IGNORE item_barcodes SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.MOVE_EXTRA_BARCODES);
            assertTakes(2, ItemMergeStatements.MOVE_EXTRA_BARCODES);

            assertTrue(ItemMergeStatements.KEEP_ITEM_BARCODE.startsWith("INSERT IGNORE INTO item_barcodes"));
            assertTrue(ItemMergeStatements.KEEP_UNIT_BARCODES.startsWith("INSERT IGNORE INTO item_barcodes"));
        }

        /**
         * The code printed on the packet - the whole reason for merging - kept before the
         * row holding it is deleted, unless a unit of some other item already answers to it.
         */
        @Test
        void theItemsOwnBarcodeIsKept() {
            assertEquals("""
                    INSERT IGNORE INTO item_barcodes (item_id, barcode)
                    SELECT ?, i.barcode
                    FROM items i
                    WHERE i.id = ?
                      AND i.barcode <> ''
                      AND NOT EXISTS (SELECT 1 FROM items_units u WHERE u.items_barcode = i.barcode AND u.items_id <> ?)""",
                    ItemMergeStatements.KEEP_ITEM_BARCODE);
            assertTakes(3, ItemMergeStatements.KEEP_ITEM_BARCODE);
        }

        @Test
        void theBarcodesOfTheUnitsLeftBehindAreKept() {
            assertEquals("""
                    INSERT IGNORE INTO item_barcodes (item_id, barcode)
                    SELECT ?, u.items_barcode
                    FROM items_units u
                    WHERE u.items_id = ?
                      AND u.items_barcode IS NOT NULL
                      AND u.items_barcode <> ''
                      AND NOT EXISTS (SELECT 1 FROM items i WHERE i.barcode = u.items_barcode AND i.id <> ?)""",
                    ItemMergeStatements.KEEP_UNIT_BARCODES);
            assertTakes(3, ItemMergeStatements.KEEP_UNIT_BARCODES);
        }
    }

    @Nested
    @DisplayName("packages")
    class Packages {

        @Test
        void bothColumnsMove() {
            assertEquals("UPDATE items_package SET item_id = ? WHERE item_id = ?",
                    ItemMergeStatements.MOVE_PACKAGE_ITEM);
            assertEquals("UPDATE items_package SET package_id = ? WHERE package_id = ?",
                    ItemMergeStatements.MOVE_PACKAGE_PACKAGE);
            assertTakes(2, ItemMergeStatements.MOVE_PACKAGE_ITEM);
            assertTakes(2, ItemMergeStatements.MOVE_PACKAGE_PACKAGE);
        }

        /** The earliest row is the one kept, as V5 did when it de-duplicated items_units. */
        @Test
        void duplicatesAndSelfReferencesAreCleanedUp() {
            assertEquals("""
                    DELETE p FROM items_package p
                             JOIN items_package keep
                               ON keep.item_id = p.item_id AND keep.package_id = p.package_id AND keep.id < p.id""",
                    ItemMergeStatements.DELETE_DUPLICATE_PACKAGES);
            assertEquals("DELETE FROM items_package WHERE item_id = package_id",
                    ItemMergeStatements.DELETE_SELF_PACKAGES);
            assertTakes(0, ItemMergeStatements.DELETE_DUPLICATE_PACKAGES);
            assertTakes(0, ItemMergeStatements.DELETE_SELF_PACKAGES);
        }
    }

    @Nested
    @DisplayName("the log")
    class Log {

        @Test
        void theLogRow() {
            assertEquals("""
                    INSERT INTO item_merge (target_item_id, target_item_name, source_item_id, source_item_name,
                                            source_barcode, source_first_balance, locked_period_lines, user_id, user_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", ItemMergeStatements.INSERT_LOG);
            assertTakes(9, ItemMergeStatements.INSERT_LOG);
        }

        /** As many placeholders as there are columns named - the count the DAO's nine setters rely on. */
        @Test
        void everyColumnIsBound() {
            String columns = ItemMergeStatements.INSERT_LOG.substring(
                    ItemMergeStatements.INSERT_LOG.indexOf('(') + 1,
                    ItemMergeStatements.INSERT_LOG.indexOf(')'));
            assertEquals(9, columns.split(",").length);
        }

        @Test
        void aLineRowPerTable() {
            assertEquals("INSERT INTO item_merge_lines (merge_id, table_name, rows_moved) VALUES (?, ?, ?)",
                    ItemMergeStatements.INSERT_LOG_LINE);
            assertTakes(3, ItemMergeStatements.INSERT_LOG_LINE);
        }

        /** The log is keyed by {@code table.column}, so the two package columns stay apart. */
        @Test
        void theKeysWrittenToTheLogAreDistinct() {
            Map<String, Long> byKey = ItemReferenceRegistry.ALL.stream()
                    .collect(java.util.stream.Collectors.groupingBy(ItemReference::qualified,
                            java.util.stream.Collectors.counting()));
            byKey.forEach((key, count) -> assertEquals(1L, count, "two references share the log key " + key));
        }
    }
}
