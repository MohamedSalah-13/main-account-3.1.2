package com.hamza.account.features.expense.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The statements over {@code expense_budget}, pinned column for column. */
class ExpenseBudgetQueryTest {

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("year and month are quoted - both are MySQL function names")
    void reservedWordsAreQuoted() {
        for (String sql : new String[]{ExpenseBudgetQuery.BY_YEAR_SQL, ExpenseBudgetQuery.FOR_PERIOD_SQL,
                ExpenseBudgetQuery.INSERT_SQL, ExpenseBudgetQuery.YEARS_SQL}) {
            assertFalse(sql.matches("(?s).*[^`\\w.]year[^`\\w].*"), "an unquoted year in: " + sql);
        }
        assertTrue(ExpenseBudgetQuery.INSERT_SQL.contains("`year`"));
        assertTrue(ExpenseBudgetQuery.INSERT_SQL.contains("`month`"));
    }

    @Test
    @DisplayName("month_key is never written - the database computes it, and the unique index reads it")
    void generatedColumnIsNotWritten() {
        assertFalse(ExpenseBudgetQuery.INSERT_SQL.contains("month_key"));
        assertFalse(ExpenseBudgetQuery.UPDATE_SQL.contains("month_key"));
        assertTrue(ExpenseBudgetQuery.TAKEN_SQL.contains("month_key = COALESCE(?, 0)"),
                "a yearly budget is month_key 0, which is not a month and so collides with no month");
    }

    @Test
    @DisplayName("a period fetches the yearly budgets it runs through and the monthly ones it covers")
    void forPeriodTakesBoth() {
        String sql = ExpenseBudgetQuery.FOR_PERIOD_SQL;
        assertTrue(sql.contains("b.`month` IS NULL AND b.`year` BETWEEN ? AND ?"));
        assertTrue(sql.contains("b.`month` IS NOT NULL AND (b.`year` * 100 + b.`month`) BETWEEN ? AND ?"));
        assertEquals(4, placeholders(sql));
    }

    @Test
    @DisplayName("the update moves neither the heading nor the period: that would be a different budget")
    void updateOwnsOnlyWhatTheFormHas() {
        String set = ExpenseBudgetQuery.UPDATE_SQL.substring(ExpenseBudgetQuery.UPDATE_SQL.indexOf("SET"));
        assertFalse(set.contains("heading_id"));
        assertFalse(set.contains("year"));
        assertFalse(set.contains("month"));
        assertFalse(set.contains("user_id"));
        assertEquals(3, placeholders(ExpenseBudgetQuery.UPDATE_SQL), "amount, notes and the key");
        assertEquals(6, placeholders(ExpenseBudgetQuery.INSERT_SQL));
        assertEquals(4, placeholders(ExpenseBudgetQuery.TAKEN_SQL));
        assertEquals(1, placeholders(ExpenseBudgetQuery.BY_YEAR_SQL));
        assertEquals(0, placeholders(ExpenseBudgetQuery.YEARS_SQL));
    }

    @Test
    @DisplayName("the heading is inner-joined and its parent LEFT, so a main heading keeps its row")
    void joins() {
        assertTrue(ExpenseBudgetQuery.BY_YEAR_SQL.contains("JOIN expenses h ON h.id = b.heading_id"));
        assertTrue(ExpenseBudgetQuery.BY_YEAR_SQL.contains("LEFT JOIN expenses p ON p.id = h.parent_id"));
    }
}
