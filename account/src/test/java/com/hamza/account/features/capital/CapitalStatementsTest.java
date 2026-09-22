package com.hamza.account.features.capital;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the capital statements with their parameter counts, and the one rule about direction. */
class CapitalStatementsTest {

    @Test
    void theOwnersMovementsByDayAndTreasury() {
        assertEquals("""
                SELECT d.date_inter AS day,
                       d.treasury_id AS treasury_id,
                       t.t_name AS treasury_name,
                       COALESCE(SUM(IF(d.deposit_or_expenses = 1, d.amount, 0)), 0) AS paid_in,
                       COALESCE(SUM(IF(d.deposit_or_expenses = 2, d.amount, 0)), 0) AS drawn,
                       COUNT(*) AS movements
                FROM treasury_deposit_expenses d
                         JOIN treasury t ON t.id = d.treasury_id
                WHERE d.category <> 'NORMAL'
                  AND d.date_inter BETWEEN ? AND ?
                GROUP BY d.date_inter, d.treasury_id, t.t_name
                ORDER BY d.date_inter, t.t_name""", CapitalStatements.BY_DAY_AND_TREASURY);
    }

    @Test
    void whatCameBeforeThePeriod() {
        assertEquals("""
                SELECT COALESCE(SUM(IF(d.deposit_or_expenses = 1, d.amount, 0)), 0) AS paid_in,
                       COALESCE(SUM(IF(d.deposit_or_expenses = 2, d.amount, 0)), 0) AS drawn
                FROM treasury_deposit_expenses d
                WHERE d.category <> 'NORMAL'
                  AND d.date_inter < ?""", CapitalStatements.BEFORE);
    }

    @Test
    void everyOpeningFigureIsBroughtForward() {
        assertEquals("""
                SELECT (SELECT COALESCE(SUM(t.amount), 0) FROM treasury t) AS treasuries,
                       (SELECT COALESCE(SUM(c.first_balance), 0) FROM custom c) AS customers,
                       (SELECT COALESCE(SUM(s.first_balance), 0) FROM suppliers s) AS suppliers,
                       (SELECT COALESCE(SUM(st.first_balance * i.buy_price), 0)
                        FROM items_stock st JOIN items i ON i.id = st.item_id) AS stock""",
                CapitalStatements.BROUGHT_FORWARD);
    }

    @Test
    void eachParameterCountIsItsStatementsOwn() {
        assertEquals(CapitalStatements.BY_DAY_AND_TREASURY_PARAMETERS, marks(CapitalStatements.BY_DAY_AND_TREASURY));
        assertEquals(CapitalStatements.BEFORE_PARAMETERS, marks(CapitalStatements.BEFORE));
        assertEquals(CapitalStatements.BROUGHT_FORWARD_PARAMETERS, marks(CapitalStatements.BROUGHT_FORWARD));
    }

    /**
     * The side a movement is on is its direction column, never its category's name: a category
     * added later would otherwise vanish from both sides at once.
     */
    @Test
    void theDirectionIsReadFromTheDirectionColumn() {
        for (String sql : new String[]{CapitalStatements.BY_DAY_AND_TREASURY, CapitalStatements.BEFORE}) {
            assertTrue(sql.contains("d.category <> 'NORMAL'"));
            assertFalse(sql.contains("'CAPITAL_IN'"));
            assertFalse(sql.contains("'OWNER_DRAW'"));
        }
    }

    private static long marks(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }
}
