package com.hamza.account.features.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The statements over {@code type_price}, character for character. */
class PriceTierQueryTest {

    @Test
    void statements() {
        assertEquals("""
                SELECT id, name, is_active, rule_source, rule_tier_id, rule_percent, rule_rounding
                FROM type_price
                ORDER BY id""", PriceTierQuery.ALL_SQL);
        assertEquals("SELECT id FROM type_price ORDER BY id FOR UPDATE", PriceTierQuery.LOCK_SQL);
        assertEquals("""
                UPDATE type_price
                SET name = ?, is_active = ?, rule_source = ?, rule_tier_id = ?, rule_percent = ?, rule_rounding = ?
                WHERE id = ?""", PriceTierQuery.UPDATE_SQL);
        assertEquals("UPDATE type_price SET name = CONCAT('#', id) WHERE id = ?", PriceTierQuery.CLEAR_NAME_SQL);
        assertEquals("""
                SELECT price_id, COUNT(*)
                FROM custom
                WHERE is_active = 1
                GROUP BY price_id""", PriceTierQuery.CUSTOMERS_BY_TIER_SQL);
    }
}
