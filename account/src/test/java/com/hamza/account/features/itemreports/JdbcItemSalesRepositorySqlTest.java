package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Pareto statements' shape, without a database - a column MySQL does not have is otherwise found on a screen. */
class JdbcItemSalesRepositorySqlTest {

    @Test
    void eachSideIsGroupedPerItemBeforeTheUnion() {
        String sql = JdbcItemSalesRepository.salesSql(ItemCatalogSql.build(ItemCatalogFilter.EMPTY), false);

        assertTrue(sql.contains("SELECT d.num AS item_id, SUM(d.quantity * d.type_value) AS quantity, "
                + "SUM(d.total_sel_price - d.discount) AS net, SUM(d.total_buy_price) AS cost "
                + "FROM sales d JOIN total_sales h ON h.invoice_number = d.invoice_number "
                + "WHERE h.invoice_date BETWEEN ? AND ? GROUP BY d.num"));
        assertTrue(sql.contains("SELECT r.item_id AS item_id, -SUM(r.quantity * r.type_value) AS quantity, "
                + "-SUM(r.total_sel_price - r.discount) AS net, -SUM(r.total_buy_price) AS cost "
                + "FROM sales_re r JOIN total_sales_re rh ON rh.id = r.invoice_number"));
        assertTrue(sql.indexOf("UNION ALL") < sql.indexOf("GROUP BY m.item_id"));
    }

    /** The movement row aggregates the catalogue; it is joined only when a balance condition reads it. */
    @Test
    void theMovementRowIsJoinedOnlyWhenTheFilterReadsABalance() {
        ItemCatalogFilter inStock = ItemCatalogFilter.EMPTY.withBalance(ItemCatalogFilter.BalanceRule.IN_STOCK);

        assertFalse(JdbcItemSalesRepository.salesSql(ItemCatalogSql.build(ItemCatalogFilter.EMPTY), false)
                .contains(ItemCatalogSql.MOVEMENTS));
        assertTrue(ItemCatalogSql.requiresMovementJoin(inStock));
        assertTrue(JdbcItemSalesRepository.salesSql(ItemCatalogSql.build(inStock), true)
                .contains("JOIN " + ItemCatalogSql.MOVEMENTS + " ip ON items.id = ip.item_id"));
    }

    @Test
    void thePeriodComesBeforeTheFiltersOwnParameters() {
        ItemCatalogSql.Statement group = ItemCatalogSql.build(ItemCatalogFilter.EMPTY.withGroup(4, 9));
        String sql = JdbcItemSalesRepository.salesSql(group, false);

        assertEquals(JdbcItemSalesRepository.PERIOD_PARAMETERS + group.whereParameters().size(), marks(sql));
        assertTrue(sql.endsWith(group.where()));
    }

    @Test
    void theInvoiceDiscountsAreTheSalesLessTheReturnsEachOnItsOwnDate() {
        assertEquals("""
                SELECT (SELECT COALESCE(SUM(h.discount), 0) FROM total_sales h WHERE h.invoice_date BETWEEN ? AND ?)
                     - (SELECT COALESCE(SUM(r.discount), 0) FROM total_sales_re r WHERE r.invoice_date BETWEEN ? AND ?) AS header_discounts""",
                JdbcItemSalesRepository.HEADER_DISCOUNTS);
        assertEquals(JdbcItemSalesRepository.HEADER_DISCOUNTS_PARAMETERS, marks(JdbcItemSalesRepository.HEADER_DISCOUNTS));
    }

    private static int marks(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}
