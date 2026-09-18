package com.hamza.account.features.expense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements over {@code expenses_details}, pinned: a column swapped or a condition bound out of order
 * still runs - it just answers a different question.
 */
class ExpenseQueryTest {

    private static final ExpenseFilter EVERYTHING = new ExpenseFilter(LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30), 3, 1, 9, BigDecimal.ONE, BigDecimal.TEN, "كهرباء", 2, 50);
    private static final ExpenseFilter NOTHING = new ExpenseFilter(null, null, null, null, null, null, null, "", 0, 50);

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    @Test
    @DisplayName("the WHERE a full filter writes, character for character")
    void whereIsPinned() {
        assertEquals("""
                WHERE d.date >= ?
                  AND d.date <= ?
                  AND (d.type_code = ? OR h.parent_id = ?)
                  AND d.treasury_id = ?
                  AND d.user_id = ?
                  AND d.amount >= ?
                  AND d.amount <= ?
                  AND (d.id = ? OR h.expenses_name LIKE ? ESCAPE '!' OR p.expenses_name LIKE ? ESCAPE '!' \
                OR d.payee LIKE ? ESCAPE '!' OR d.reference_no LIKE ? ESCAPE '!' OR d.notes LIKE ? ESCAPE '!' \
                OR e.column_name LIKE ? ESCAPE '!')
                """, ExpenseQuery.whereSql(EVERYTHING));
        assertEquals("", ExpenseQuery.whereSql(NOTHING));
    }

    @Test
    @DisplayName("the binder, the declared count and the written placeholders agree for every shape")
    void bindingMatchesTheStatement() {
        for (ExpenseFilter filter : List.of(EVERYTHING, NOTHING,
                ExpenseFilter.between(LocalDate.of(2026, 1, 1), null),
                new ExpenseFilter(null, null, 3, null, null, null, null, "42", 0, 50))) {
            List<Object> bound = new ArrayList<>();
            JdbcExpenseRepository.bindWhere(bound, filter);
            assertEquals(placeholders(ExpenseQuery.whereSql(filter)), bound.size(), filter.toString());
            assertEquals(ExpenseQuery.whereParameterCount(filter), bound.size(), filter.toString());
            assertEquals(bound.size() + 2, placeholders(ExpenseQuery.pageSql(filter)), "limit and offset");
            assertEquals(bound.size(), placeholders(ExpenseQuery.summarySql(filter)));
            assertEquals(bound.size(), placeholders(ExpenseQuery.topHeadingSql(filter)));
        }
    }

    @Test
    @DisplayName("the page, the summary and the top heading are built from one WHERE")
    void oneWhere() {
        String where = ExpenseQuery.whereSql(EVERYTHING);
        assertTrue(ExpenseQuery.pageSql(EVERYTHING).contains(where));
        assertTrue(ExpenseQuery.summarySql(EVERYTHING).contains(where));
        assertTrue(ExpenseQuery.topHeadingSql(EVERYTHING).contains(where));
    }

    @Test
    @DisplayName("a main heading takes in the headings under it")
    void mainHeadingIncludesChildren() {
        List<Object> bound = new ArrayList<>();
        JdbcExpenseRepository.bindWhere(bound, new ExpenseFilter(null, null, 3, null, null, null, null, "", 0, 50));
        assertEquals(List.of(3, 3), bound);
    }

    @Test
    @DisplayName("typed wildcards are escaped, not obeyed")
    void wildcardsAreEscaped() {
        assertEquals("%50!%!_off%", ExpenseQuery.containsPattern("50%_off"));
        assertEquals("a!!b%", ExpenseQuery.startsPattern("a!b"));
    }

    @Test
    @DisplayName("every join but the heading is LEFT, so a gone treasury, employee or user drops no expense")
    void joins() {
        String page = ExpenseQuery.pageSql(NOTHING);
        assertTrue(page.contains("JOIN expenses h ON h.id = d.type_code"));
        assertTrue(page.contains("LEFT JOIN expenses p ON p.id = h.parent_id"));
        assertTrue(page.contains("LEFT JOIN treasury t ON t.id = d.treasury_id"));
        assertTrue(page.contains("LEFT JOIN employees e ON e.id = d.emp_id"));
        assertTrue(page.contains("LEFT JOIN users u ON u.id = d.user_id"));
        assertFalse(page.contains("expenses_details_view"), "the view carries none of V64's columns");
    }

    @Test
    @DisplayName("the update never names the employee, who entered it, the shift, or the template")
    void updateLeavesWhatTheFormDoesNotOwn() {
        String set = ExpenseQuery.UPDATE_SQL.substring(ExpenseQuery.UPDATE_SQL.indexOf("SET"));
        assertFalse(set.contains("emp_id"), "an edit would take the employee off every salary it touched");
        assertFalse(set.contains("user_id"));
        assertFalse(set.contains("shift_id"));
        // V66: where an expense came from is recorded once, at the insert. An edit that could write it
        // would let a row be moved onto a template it was never recorded from - and that column is what
        // decides whether the template's period has been answered.
        assertFalse(set.contains("recurring_id"));
        assertEquals(8, placeholders(ExpenseQuery.UPDATE_SQL));
        assertTrue(ExpenseQuery.INSERT_SQL.contains("recurring_id"));
        assertEquals(11, placeholders(ExpenseQuery.INSERT_SQL));
    }

    @Test
    @DisplayName("the headings: a main heading has no parent, so the parent join is LEFT; the update owns no key")
    void headingStatements() {
        assertTrue(ExpenseHeadingQuery.ALL_SQL.contains("LEFT JOIN expenses p ON p.id = h.parent_id"));
        assertFalse(ExpenseHeadingQuery.UPDATE_SQL.contains("system_key"),
                "no screen may give a heading a key or take one away");
        assertFalse(ExpenseHeadingQuery.UPDATE_SQL.contains("user_id"));
        assertEquals(5, placeholders(ExpenseHeadingQuery.UPDATE_SQL));
        assertEquals(5, placeholders(ExpenseHeadingQuery.INSERT_SQL));
    }
}
