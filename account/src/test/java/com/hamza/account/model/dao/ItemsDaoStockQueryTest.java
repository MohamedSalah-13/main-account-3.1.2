package com.hamza.account.model.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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
        @DisplayName("the three FILTER_ITEMS_SQL_* searches also aggregate")
        void filterQueriesAggregate() throws Exception {
            for (String name : new String[]{
                    "FILTER_ITEMS_SQL_TEXT_STARTS", "FILTER_ITEMS_SQL_TEXT_CONTAINS", "FILTER_ITEMS_SQL_NUMERIC"}) {
                String sql = field(name);
                assertTrue(sql.contains("GROUP BY item_id"), name + " must aggregate across stocks");
            }
        }
    }

    @Nested
    @DisplayName("Stock-scoped finders - a warehouse is named, must stay row-per-stock")
    class StockScopedQueries {

        @Test
        @DisplayName("QUERY_ITEMS stays the raw (item, stock) join")
        void queryItemsStaysUnaggregated() throws Exception {
            String sql = field("QUERY_ITEMS");
            assertFalse(sql.contains("GROUP BY"),
                    "findItemByIdAndStockId and its siblings filter on ip.stock_id = ?, which only "
                            + "makes sense against the real per-stock rows - aggregating first would "
                            + "make the filter pick an arbitrary warehouse instead of the one asked for");
            assertTrue(sql.contains("ip.first_balance AS stock_first_balance"),
                    "the per-stock and all-stock result sets must expose the opening under the same name");
        }
    }
}
