package com.hamza.account.model.dao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardItemDaoStatementsTest {

    @Test
    void expiryBalanceQueryAggregatesAllFourStockMovementsInBaseUnits() {
        String sql = new CardItemDao().expiryBalanceSql()
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(sql.contains("from purchase p"));
        assertTrue(sql.contains("from sales_re r"));
        assertTrue(sql.contains("from sales s"));
        assertTrue(sql.contains("from purchase_re r"));
        assertTrue(sql.contains("quantity * p.type_value as base_quantity"));
        assertTrue(sql.contains("-(s.quantity * s.type_value) as base_quantity"));
        assertEquals(4, occurrences(sql, "h.stock_id = ?"));
        assertTrue(sql.contains("group by expiration_date"));
        assertEquals(8, sql.chars().filter(character -> character == '?').count());
    }

    private static int occurrences(String text, String pattern) {
        return (text.length() - text.replace(pattern, "").length()) / pattern.length();
    }
}
