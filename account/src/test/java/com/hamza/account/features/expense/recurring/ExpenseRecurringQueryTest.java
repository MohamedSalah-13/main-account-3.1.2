package com.hamza.account.features.expense.recurring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements over {@code expense_recurring}, pinned the way the document and party specs are: a
 * merge that swaps two adjacent columns still produces valid SQL, it just writes the day of the month
 * into the treasury.
 */
class ExpenseRecurringQueryTest {

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("the list shows the stopped templates; only the active ones can fall due")
    void bothReads() {
        assertFalse(ExpenseRecurringQuery.ALL_SQL.contains("WHERE r.is_active"),
                "this is the screen a stopped template is restarted from");
        assertTrue(ExpenseRecurringQuery.ACTIVE_SQL.contains("WHERE r.is_active = 1"));
    }

    @Test
    @DisplayName("the treasury join is LEFT, so a till that has gone drops no template out of the list")
    void treasuryJoinIsLeft() {
        assertTrue(ExpenseRecurringQuery.ALL_SQL.contains("LEFT JOIN treasury t ON t.id = r.treasury_id"));
        assertTrue(ExpenseRecurringQuery.ALL_SQL.contains("JOIN expenses h ON h.id = r.heading_id"));
        assertTrue(ExpenseRecurringQuery.ALL_SQL.contains("LEFT JOIN expenses p ON p.id = h.parent_id"),
                "a main heading has no parent");
    }

    @Test
    @DisplayName("\"already recorded\" is read off recurring_id, never guessed from the heading and amount")
    void recordedIsTheLink() {
        String sql = ExpenseRecurringQuery.RECORDED_PERIODS_SQL;
        assertTrue(sql.contains("d.recurring_id IS NOT NULL"));
        assertTrue(sql.contains("JOIN expense_recurring r ON r.id = d.recurring_id"));
        assertFalse(sql.contains("d.amount"), "a second rent in the same month is not last month's reminder");
        assertFalse(sql.contains("d.type_code"));
        assertEquals(1, placeholders(sql), "the date it looks back to");
        assertEquals(1, placeholders(ExpenseRecurringQuery.RECORDED_COUNT_SQL));
    }

    @Test
    @DisplayName("the update writes neither the key nor who created the template")
    void updateOwnsNeitherKeyNorAuthor() {
        String sql = ExpenseRecurringQuery.UPDATE_SQL;
        String set = sql.substring(sql.indexOf("SET"), sql.indexOf("WHERE"));
        assertFalse(set.contains("user_id"), "who changed it afterwards is audit_log's answer");
        assertTrue(sql.contains("WHERE id = ?"), "the key belongs in the WHERE, never in the SET");
        // Ten columns and the key; the insert writes the same ten and the author.
        assertEquals(11, placeholders(ExpenseRecurringQuery.UPDATE_SQL));
        assertEquals(11, placeholders(ExpenseRecurringQuery.INSERT_SQL));
        assertTrue(ExpenseRecurringQuery.INSERT_SQL.contains("user_id"));
        assertEquals(1, placeholders(ExpenseRecurringQuery.DELETE_SQL));
        assertEquals(1, placeholders(ExpenseRecurringQuery.BY_ID_SQL));
    }

    @Test
    @DisplayName("the stopped templates sort last, then by the heading's path")
    void ordering() {
        assertTrue(ExpenseRecurringQuery.ALL_SQL.contains("ORDER BY r.is_active DESC"));
    }
}
