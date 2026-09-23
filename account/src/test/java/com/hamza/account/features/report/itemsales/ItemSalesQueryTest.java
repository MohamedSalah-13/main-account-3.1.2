package com.hamza.account.features.report.itemsales;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSalesQueryTest {

    private static final ItemCatalogSql.Statement NOTHING = ItemCatalogSql.build(ItemCatalogFilter.EMPTY);

    @Test
    @DisplayName("the cost is not selected at all for a reader who may not see a profit")
    void theCostIsSelectedOnlyWhenAsked() {
        String without = ItemSalesQuery.rowsSql(NOTHING, false);
        String with = ItemSalesQuery.rowsSql(NOTHING, true);

        assertFalse(without.contains("total_buy_price"), without);
        assertFalse(without.contains("cost"), without);
        assertTrue(with.contains("SUM(d.total_buy_price) AS cost"), with);
        assertTrue(with.contains("-SUM(r.total_buy_price) AS cost"), "a return's cost is taken off");
    }

    @Test
    @DisplayName("each side is grouped before the union, and each reads its own header's date")
    void eachSideIsGroupedBeforeTheUnion() {
        String sql = ItemSalesQuery.rowsSql(NOTHING, true);
        int union = sql.indexOf("UNION ALL");

        assertTrue(sql.substring(0, union).contains("GROUP BY d.num"), sql);
        assertTrue(sql.substring(union).contains("GROUP BY r.item_id"), sql);
        assertTrue(sql.contains("JOIN total_sales h ON h.invoice_number = d.invoice_number"), sql);
        assertTrue(sql.contains("JOIN total_sales_re rh ON rh.id = r.invoice_number"), sql);
        assertTrue(sql.contains("SUM(d.total_sel_price - d.discount) AS sold"), "a line after its own discount");
        assertTrue(sql.contains("SUM(r.quantity * r.type_value) AS returned_quantity"), "base units");
        assertEquals(ItemSalesQuery.ROWS_PERIOD_PARAMETERS, placeholders(sql));
    }

    @Test
    @DisplayName("a text search is the items list's own WHERE, with its parameters after the period's")
    void theSearchIsTheItemsListsOwn() {
        ItemCatalogSql.Statement rice = ItemCatalogSql.build(ItemCatalogFilter.EMPTY.withSearch("rice"));
        String sql = ItemSalesQuery.rowsSql(rice, false);

        assertTrue(sql.endsWith(rice.where()), sql);
        assertEquals(ItemSalesQuery.ROWS_PERIOD_PARAMETERS + rice.whereParameters().size(), placeholders(sql));
    }

    @Test
    @DisplayName("an item's lines are gathered by unit and price, the sales before the returns")
    void theLines() {
        String sql = ItemSalesQuery.linesSql();

        assertEquals(ItemSalesQuery.LINES_PARAMETERS, placeholders(sql));
        assertTrue(sql.contains("GROUP BY d.type, u.unit_name, d.type_value, d.price"), sql);
        assertTrue(sql.contains("GROUP BY r.type, u.unit_name, r.type_value, r.price"), sql);
        assertTrue(sql.contains("AND d.num = ?") && sql.contains("AND r.item_id = ?"), sql);
        assertTrue(sql.endsWith("ORDER BY is_return, factor DESC, price DESC"), sql);
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }
}
