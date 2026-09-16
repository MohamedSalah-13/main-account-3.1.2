package com.hamza.account.model.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one statement in this codebase that reads two barcode columns at once.
 * <p>
 * A code can live in three tables, and the question "is this code taken" unions all
 * three. MySQL refuses a UNION of two columns whose collations differ, and on a
 * customer database on 2026-09-16 they did: no migration here names a charset, so a
 * table a migration creates takes the database default while a table restored from a
 * mysqldump keeps the charset the dump names and lands on the server's default
 * collation instead. `item_barcodes` was `utf8mb4_unicode_ci` where the other ninety
 * tables were `utf8mb4_0900_ai_ci`, and error 1271 came back for every attempt to save
 * an item or to be offered a free code - with nothing wrong with the data, and the same
 * build working everywhere else.
 * <p>
 * V63 converges the columns. This is the half that holds on a database which has not
 * run it, and the conversion belongs in the select list rather than the {@code WHERE},
 * so each table is still read through its own barcode index.
 */
class ItemsBarcodeQueryTest {

    @Test
    @DisplayName("every unioned column is converted to one collation")
    void eachBranchIsConverted() {
        String sql = ItemsDao.takenBarcodesSql(2, false);

        assertTrue(sql.contains("SELECT CONVERT(barcode USING utf8mb4) AS code FROM items "),
                "the item's own code is unioned raw: " + sql);
        assertTrue(sql.contains("SELECT CONVERT(barcode USING utf8mb4) AS code FROM item_barcodes "),
                "an extra code is unioned raw: " + sql);
        assertTrue(sql.contains("SELECT CONVERT(items_barcode USING utf8mb4) AS code FROM items_units "),
                "a unit's code is unioned raw: " + sql);
    }

    @Test
    @DisplayName("the conversion stays out of the filter, so the barcode indexes are still used")
    void theFilterReadsTheColumnItself() {
        String sql = ItemsDao.takenBarcodesSql(1, false);

        assertTrue(sql.contains("AND barcode IN (?)"), sql);
        assertTrue(sql.contains("AND items_barcode IN (?)"), sql);
    }

    @Test
    @DisplayName("one placeholder per code per table, plus the item each table excludes")
    void everyPlaceholderIsBound() {
        for (int codes = 1; codes <= 4; codes++) {
            String sql = ItemsDao.takenBarcodesSql(codes, false);

            assertEquals(3 * (codes + 1), sql.chars().filter(character -> character == '?').count(),
                    "placeholders for " + codes + " codes");
        }
    }

    @Test
    @DisplayName("asking for the first answer asks the database for one row")
    void firstOnlyLimits() {
        assertTrue(ItemsDao.takenBarcodesSql(1, true).endsWith("LIMIT 1"));
        assertTrue(ItemsDao.takenBarcodesSql(1, false).stripTrailing().endsWith("AS matches"));
    }
}
