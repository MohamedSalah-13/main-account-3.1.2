package com.hamza.account.features.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link CurrencyQuery}: the writes character for character with the number of values
 * {@link JdbcCurrencyRepository} binds to each, and the two readings of "the rate in force" held to one
 * definition. A statement that swaps two columns is still valid SQL - it saves a symbol as a name - so
 * the text is the only thing that can catch it.
 */
class CurrencyQueryTest {

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    @Test
    @DisplayName("the currency writes, and how many values each binds")
    void currencyWrites() {
        assertEquals("""
                INSERT INTO currency (code, name, symbol, decimal_places, is_active, sort_order, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", CurrencyQuery.INSERT_SQL);
        assertEquals("""
                UPDATE currency
                SET code = ?, name = ?, symbol = ?, decimal_places = ?, is_active = ?, sort_order = ?
                WHERE id = ?""", CurrencyQuery.UPDATE_SQL);
        assertEquals(7, parameters(CurrencyQuery.INSERT_SQL));
        assertEquals(7, parameters(CurrencyQuery.UPDATE_SQL));
        assertEquals("DELETE FROM currency WHERE id = ?", CurrencyQuery.DELETE_SQL);
    }

    @Test
    @DisplayName("no form write can touch the base flag or who created the row")
    void theFormDoesNotOwnTheBase() {
        assertFalse(CurrencyQuery.UPDATE_SQL.contains("is_base"));
        assertFalse(CurrencyQuery.INSERT_SQL.contains("is_base"));
        assertFalse(CurrencyQuery.UPDATE_SQL.contains("user_id"));
        assertEquals("UPDATE currency SET is_base = 0 WHERE is_base = 1", CurrencyQuery.CLEAR_BASE_SQL);
        assertEquals("UPDATE currency SET is_base = 1 WHERE id = ? AND is_active = 1", CurrencyQuery.MARK_BASE_SQL);
    }

    @Test
    @DisplayName("the base moves under an exclusive lock of every currency, a rate is written under a shared one")
    void locks() {
        assertEquals("SELECT c.id FROM currency c ORDER BY c.id FOR UPDATE", CurrencyQuery.LOCK_ALL_SQL);
        assertTrue(CurrencyQuery.LOCK_FOR_RATE_SQL.endsWith("WHERE c.id = ? FOR SHARE"));
        assertEquals("SELECT COUNT(*) FROM currency_rate", CurrencyQuery.RATE_COUNT_SQL);
    }

    @Test
    @DisplayName("the rate writes, and an edit never moves a rate to another currency")
    void rateWrites() {
        assertEquals("""
                INSERT INTO currency_rate (currency_id, effective_date, rate, notes, user_id)
                VALUES (?, ?, ?, ?, ?)""", CurrencyQuery.INSERT_RATE_SQL);
        assertEquals("""
                UPDATE currency_rate
                SET effective_date = ?, rate = ?, notes = ?
                WHERE id = ?""", CurrencyQuery.UPDATE_RATE_SQL);
        assertFalse(CurrencyQuery.UPDATE_RATE_SQL.contains("currency_id"));
        assertEquals(5, parameters(CurrencyQuery.INSERT_RATE_SQL));
        assertEquals(4, parameters(CurrencyQuery.UPDATE_RATE_SQL));
        assertEquals(3, parameters(CurrencyQuery.RATE_DAY_TAKEN_SQL));
        assertEquals("DELETE FROM currency_rate WHERE id = ?", CurrencyQuery.DELETE_RATE_SQL);
    }

    @Test
    @DisplayName("both readings of the rate in force stop at the day and take the latest before it")
    void oneDefinitionOfTheRateInForce() {
        for (String sql : new String[]{CurrencyQuery.RATE_ON_SQL, CurrencyQuery.RATES_IN_FORCE_SQL}) {
            assertTrue(sql.contains("effective_date <= ?"), sql);
            assertEquals(sql == CurrencyQuery.RATE_ON_SQL ? 2 : 1, parameters(sql));
        }
        assertTrue(CurrencyQuery.RATE_ON_SQL.contains("ORDER BY r.effective_date DESC\nLIMIT 2"));
        assertTrue(CurrencyQuery.RATES_IN_FORCE_SQL.contains(
                "ROW_NUMBER() OVER (PARTITION BY r.currency_id ORDER BY r.effective_date DESC) AS newest"));
        assertTrue(CurrencyQuery.RATES_IN_FORCE_SQL.contains(
                "LAG(r.rate) OVER (PARTITION BY r.currency_id ORDER BY r.effective_date) AS previous_rate"));
        assertTrue(CurrencyQuery.RATES_IN_FORCE_SQL.endsWith("WHERE ranked.newest = 1"));
    }

    @Test
    @DisplayName("the lists read the base first, and who entered a rate through a join that keeps a deleted user's rate")
    void readings() {
        assertTrue(CurrencyQuery.ALL_SQL.endsWith("ORDER BY c.is_base DESC, c.sort_order, c.code"));
        assertTrue(CurrencyQuery.BASE_SQL.endsWith("WHERE c.is_base = 1"));
        assertTrue(CurrencyQuery.RATES_OF_SQL.contains("LEFT JOIN users u ON u.id = r.user_id"));
        assertTrue(CurrencyQuery.RATES_OF_SQL.endsWith("WHERE r.currency_id = ?\nORDER BY r.effective_date DESC"));
    }
}
