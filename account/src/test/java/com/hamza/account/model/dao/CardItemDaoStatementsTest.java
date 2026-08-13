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

        assertTrue(sql.contains("from purchase "));
        assertTrue(sql.contains("from sales_re "));
        assertTrue(sql.contains("from sales "));
        assertTrue(sql.contains("from purchase_re "));
        assertTrue(sql.contains("quantity * type_value as base_quantity"));
        assertTrue(sql.contains("-(quantity * type_value) as base_quantity"));
        assertTrue(sql.contains("group by expiration_date"));
        assertEquals(4, sql.chars().filter(character -> character == '?').count());
    }
}
