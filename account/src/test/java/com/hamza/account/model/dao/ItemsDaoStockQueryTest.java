package com.hamza.account.model.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fix for the two catalog-query defects described in
 * {@code docs/erp-roadmap.md} §11: {@code quantity_items_table} is keyed by
 * (item, stock), so joining it to {@code items} directly returns one row per
 * warehouse an item has moved through, and each row's movement columns are only
 * that warehouse's share - not the item's total. That was invisible with one
 * warehouse ({@link com.hamza.account.config.DefaultStock}) and would duplicate
 * every item, and understate its balance, the moment a second one existed.
 * <p>
 * There is no database here on purpose: what a second stock breaks is the SQL
 * text itself, before a single row is ever read, so the text is what this pins.
 */
class ItemsDaoStockQueryTest {

    private static final ItemsDao DAO = new ItemsDao(DaoFactory.INSTANCE);

    private static String field(String name) throws Exception {
        Field field = ItemsDao.class.getDeclaredField(name);
        field.setAccessible(true);
        boolean isStatic = java.lang.reflect.Modifier.isStatic(field.getModifiers());
        return (String) field.get(isStatic ? null : DAO);
    }

    @Nested
    @DisplayName("Catalog queries - no stock named, must aggregate")
    class CatalogQueries {

        @Test
        @DisplayName("QUERY_ITEMS_ALL_STOCKS pre-aggregates quantity_items_table by item")
        void queryItemsAllStocksAggregatesByItem() throws Exception {
            String sql = field("QUERY_ITEMS_ALL_STOCKS");
            assertTrue(sql.contains("GROUP BY item_id"),
                    "a catalog-wide query must fold every stock's movements into one row per item");
            assertTrue(sql.contains("SUM(quantityPurchase)"),
                    "movements must be summed, not read off a single warehouse's row");
        }

        @Test
        @DisplayName("the per-warehouse opening balances are summed once")
        void openingBalancesAreSummedAcrossStocks() throws Exception {
            String movements = field("ITEM_MOVEMENTS_ALL_STOCKS");
            assertTrue(movements.contains("SUM(first_balance)"),
                    "V18 made quantity_items_table.first_balance a distinct value per warehouse; "
                            + "a catalogue-wide row must add every warehouse's opening stock");
            assertTrue(movements.contains("AS stock_first_balance"),
                    "the aggregate needs an unambiguous name distinct from items.first_balance, "
                            + "which is only the warehouse-1 compatibility mirror");
        }

        @Test
        @DisplayName("the three name searches pick ids without building any balance")
        void filterQueriesPickIdsOnly() throws Exception {
            for (String name : new String[]{
                    "FILTER_ITEM_IDS_SQL_TEXT_STARTS", "FILTER_ITEM_IDS_SQL_TEXT_CONTAINS", "FILTER_ITEM_IDS_SQL_NUMERIC"}) {
                String sql = field(name);
                assertTrue(sql.startsWith("SELECT items.id\n"), name + " answers ids; the rows are loaded for them after");
                assertFalse(sql.contains("quantity_items_table") || sql.contains("GROUP BY"),
                        name + " must not build every item's balance before its LIMIT applies");
                assertTrue(sql.contains("WHERE items.id IN (SELECT item_id FROM items_stock)\n  AND ("),
                        name + " keeps the inner join's rule, and brackets its ORs so the rule applies to all of them");
            }
        }

        @Test
        @DisplayName("the rows for chosen ids fold every warehouse with the list's own aggregate")
        void rowsForChosenIdsAggregateLikeTheList() throws Exception {
            java.lang.reflect.Method method = ItemsDao.class.getDeclaredMethod("queryItemsAcrossStocks", int.class);
            method.setAccessible(true);
            String sql = (String) method.invoke(null, 2);
            assertTrue(sql.contains(com.hamza.account.features.items.ItemStockBalanceSql.acrossStocksForItems(2)),
                    "the rows are ItemStockBalanceSql's, folded by ItemCatalogSql's aggregate");
            assertTrue(sql.contains("GROUP BY item_id"));
            assertFalse(sql.contains("quantity_items_table"));
            assertTrue(sql.endsWith(" ip ON items.id = ip.item_id"));
            assertEquals(2, sql.chars().filter(c -> c == '?').count());
        }
    }

    @Nested
    @DisplayName("Stock-scoped finders - a warehouse is named, must stay row-per-stock")
    class StockScopedQueries {

        @Test
        @DisplayName("QUERY_ITEM_IN_STOCK reads one (item, stock) row, not the view")
        void queryItemInStockReadsOneRow() throws Exception {
            String sql = field("QUERY_ITEM_IN_STOCK");
            assertFalse(sql.contains("GROUP BY"),
                    "findItemByIdAndStockId and its siblings name one warehouse - aggregating "
                            + "would make it pick an arbitrary warehouse instead of the one asked for");
            assertFalse(sql.contains("quantity_items_table"),
                    "joining the view builds every item's balance before returning this one - "
                            + "the cost of a barcode scan grew with every invoice ever saved");
            assertTrue(sql.endsWith("WHERE ist.stock_id = ? AND ist.item_id IN (?)) ip ON items.id = ip.item_id"),
                    "the stock is bound first, then the item - the order findItemByIdAndStockId passes them");
            assertTrue(sql.contains("ip.first_balance AS stock_first_balance"),
                    "the per-stock and all-stock result sets must expose the opening under the same name");
        }

        @Test
        @DisplayName("a code is resolved to an item on the three indexed code columns")
        void aCodeIsResolvedOnTheThreeCodeColumns() throws Exception {
            String sql = field("ITEM_IDS_BY_CODE");
            assertTrue(sql.contains("FROM items WHERE barcode = ?"));
            assertTrue(sql.contains("FROM item_barcodes WHERE barcode = ?"));
            assertTrue(sql.contains("FROM items_units WHERE items_barcode = ?"));
            assertEquals(3, sql.chars().filter(c -> c == '?').count());
        }
    }
}
