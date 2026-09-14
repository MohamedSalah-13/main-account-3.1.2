package com.hamza.account.features.unitprices;

import com.hamza.account.features.items.ItemCatalogSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every statement binds exactly what it declares, and lists what the items screen calls "more than one unit". */
class UnitPriceQueryTest {

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    private static void assertBound(UnitPriceQuery.Statement statement) {
        assertEquals(placeholders(statement.sql()), statement.parameters().size(), statement.sql());
    }

    @Test
    @DisplayName("every combination of filters binds what it declares, page and count alike")
    void everyCombinationBinds() {
        for (String search : List.of("", "لبن", "6221")) {
            for (UnitPriceFilter.PriceState state : UnitPriceFilter.PriceState.values()) {
                for (boolean belowCost : new boolean[]{false, true}) {
                    for (Set<Integer> ids : List.of(Set.<Integer>of(), Set.of(4, 9))) {
                        UnitPriceFilter filter = new UnitPriceFilter(search, state, belowCost, ids);
                        assertBound(UnitPriceQuery.page(filter, 50, 100));
                        assertBound(UnitPriceQuery.count(filter));
                        assertBound(UnitPriceQuery.all(filter));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("only items with a unit besides their own are listed - the items screen's own predicate")
    void listsOnlyMultiUnitItems() {
        String where = UnitPriceQuery.where(UnitPriceFilter.EMPTY).sql();

        assertTrue(where.contains(ItemCatalogSql.HAS_EXTRA_UNITS));
        assertEquals(" WHERE " + ItemCatalogSql.HAS_EXTRA_UNITS, where);
    }

    @Test
    @DisplayName("the page and its count share one WHERE")
    void pageAndCountAgree() {
        UnitPriceFilter filter = new UnitPriceFilter("لبن", UnitPriceFilter.PriceState.MANUAL, true, Set.of(3));
        String where = UnitPriceQuery.where(filter).sql();

        assertTrue(UnitPriceQuery.page(filter, 10, 0).sql().contains(where));
        assertTrue(UnitPriceQuery.count(filter).sql().endsWith(where));
    }

    @Test
    @DisplayName("below cost compares both sides the way a unit is priced: its own figure, else the item's times the factor")
    void belowCostUsesEffectivePrices() {
        String sql = UnitPriceQuery.BELOW_COST;

        assertTrue(sql.contains("IF(u.sel_price > 0, u.sel_price, ROUND(items.sel_price1 * u.quantity, 2))"));
        assertTrue(sql.contains("IF(u.buy_price > 0, u.buy_price, ROUND(items.buy_price * u.quantity, 2))"));
        assertTrue(sql.contains("items.sel_price3"));
    }

    @Test
    @DisplayName("a save locks the items and their units, never the unit names it reads")
    void locksOnlyWhatItWrites() {
        assertTrue(UnitPriceQuery.lockItems(List.of(1, 2)).sql().endsWith("FOR UPDATE OF items"));
        assertTrue(UnitPriceQuery.units(List.of(1, 2), true).sql().endsWith("FOR UPDATE OF u"));
        assertFalse(UnitPriceQuery.units(List.of(1, 2), false).sql().contains("FOR UPDATE"));
        assertTrue(UnitPriceQuery.units(List.of(1), false).sql().contains("u.unit <> items.unit_id"));
    }

    @Test
    @DisplayName("a unit's write names its four prices and the pair its unique key is on, and nothing else")
    void unitWriteIsTargeted() {
        UnitPriceQuery.Statement statement = UnitPriceQuery.updateUnit(7, 2, new Prices(1, 2, 3, 4), 9);

        assertBound(statement);
        assertTrue(statement.sql().contains("WHERE items_id = ? AND unit = ?"));
        assertFalse(statement.sql().contains("items_barcode"));
        assertFalse(statement.sql().contains("quantity"));
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 9, 7, 2), statement.parameters());
    }

    @Test
    @DisplayName("an item's write names its four prices and not its name, code or balance")
    void itemWriteIsTargeted() {
        UnitPriceQuery.Statement statement = UnitPriceQuery.updateItem(7, new Prices(1, 2, 3, 4), 9);

        assertBound(statement);
        assertFalse(statement.sql().contains("nameItem"));
        assertFalse(statement.sql().contains("barcode"));
        assertFalse(statement.sql().contains("first_balance"));
    }
}
