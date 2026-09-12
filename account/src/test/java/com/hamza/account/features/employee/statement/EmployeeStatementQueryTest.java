package com.hamza.account.features.employee.statement;

import com.hamza.account.features.employee.EmployeeMovementSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the statement's SQL and counts its parameters.
 * <p>
 * The reason is {@code DocumentDaoStatementsTest}'s and {@code PartyStatementQueryTest}'s: a merge
 * that swaps two adjacent columns still produces valid SQL — it just reads a credit as a debit —
 * and the only thing between that and an employee being handed the wrong statement is a test that
 * reads the text. The parameter counts matter as much: the repository binds by position, in a
 * method written by hand, and a statement bound in a different order than it is written still runs.
 */
class EmployeeStatementQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    private static EmployeeStatementFilter plain() {
        return EmployeeStatementFilter.all(7, FROM, TO);
    }

    private static EmployeeStatementFilter narrowed() {
        return plain()
                .withKinds(Set.of("DEDUCTION", "ADVANCE"))
                .withSource(EmployeeMovementSource.CASH)
                .withUser(3)
                .withAmounts(new BigDecimal("100"), new BigDecimal("900"))
                .withText("سلفة");
    }

    @Nested
    @DisplayName("the shared WHERE")
    class RowFilter {

        @Test
        @DisplayName("is the same text in the page and in the summary")
        void oneWhere() {
            String where = EmployeeStatementQuery.rowFilterSql("m");
            assertTrue(EmployeeStatementQuery.pageSql().contains(where));
            assertTrue(EmployeeStatementQuery.summarySql().contains(where),
                    "filtering the totals separately is what makes a footer describe rows the "
                            + "table does not show");
        }

        @Test
        @DisplayName("is a constant whatever the filter holds - the shape a test can pin")
        void constant() {
            assertEquals(EmployeeStatementQuery.pageSql(), EmployeeStatementQuery.pageSql());
            assertEquals(12, parameters(EmployeeStatementQuery.rowFilterSql("m")));
        }

        @Test
        @DisplayName("matches the kinds as one bound list rather than a variable IN clause")
        void kindsAreOneParameter() {
            String where = EmployeeStatementQuery.rowFilterSql("m");
            assertTrue(where.contains("FIND_IN_SET(m.entry_kind, ?)"),
                    "an IN (?, ?, ?) whose length follows the selection is a different statement "
                            + "per selection, and nothing could pin any of them: " + where);
        }

        @Test
        @DisplayName("compares an amount as credit + debit, since exactly one is ever set")
        void amountIsTheMagnitude() {
            String where = EmployeeStatementQuery.rowFilterSql("m");
            assertTrue(where.contains("(m.credit + m.debit) >= ?"));
            assertTrue(where.contains("(m.credit + m.debit) <= ?"));
        }

        @Test
        @DisplayName("escapes the wildcards a person may legitimately type")
        void textIsEscaped() {
            assertTrue(EmployeeStatementQuery.rowFilterSql("m").contains("LIKE ? ESCAPE '!'"));
            assertEquals("%50!% off%", EmployeeStatementQuery.pattern("50% off"));
        }
    }

    @Nested
    @DisplayName("the page")
    class Page {

        @Test
        @DisplayName("accumulates the running balance in SQL, seeded with what came before")
        void runningBalanceIsSeeded() {
            String sql = EmployeeStatementQuery.pageSql();
            assertTrue(sql.contains("SUM(p.credit - p.debit)"),
                    "the seed reads what the employee was owed before the period: " + sql);
            assertTrue(sql.contains("p.movement_date < ?"));
            assertTrue(sql.contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
                    "a total restarted at zero prints September as though it began owed nothing");
        }

        @Test
        @DisplayName("accumulates over the whole period and filters in an outer query")
        void filtersAreOutsideTheWindow() {
            String sql = EmployeeStatementQuery.pageSql();
            int cte = sql.indexOf("WITH period AS (");
            int window = sql.indexOf("ROWS BETWEEN UNBOUNDED PRECEDING");
            int outerFilter = sql.lastIndexOf("FIND_IN_SET(m.entry_kind, ?)");
            assertTrue(cte >= 0 && window > cte && outerFilter > window,
                    "a running balance that skips the rows a filter hid is not a balance: " + sql);
        }

        @Test
        @DisplayName("orders the accumulation totally, so one statement prints the same twice")
        void orderIsTotal() {
            assertTrue(EmployeeStatementQuery.pageSql()
                    .contains("ORDER BY m.movement_date, m.entered_at, m.source, m.source_id"));
        }

        @Test
        @DisplayName("binds the employee and the period, then the filters, then the page")
        void parameterCount() {
            // employee + from (seed), employee + from + to (period), twelve filters, limit, offset
            assertEquals(19, parameters(EmployeeStatementQuery.pageSql()));
        }
    }

    @Nested
    @DisplayName("the summary")
    class Summary {

        @Test
        @DisplayName("takes the dates alone for the two balances")
        void balancesIgnoreTheFilters() {
            String sql = EmployeeStatementQuery.summarySql();
            assertTrue(sql.contains("""
                    SELECT COALESCE(SUM(CASE WHEN m.movement_date < ?
                                             THEN m.credit - m.debit ELSE 0 END), 0)
                               AS opening_balance"""),
                    "a balance narrowed by kind is a number nobody is owed, and it is the figure "
                            + "an employee signs for: " + sql);
            assertTrue(sql.contains("""
                    COALESCE(SUM(CASE WHEN m.movement_date <= ?
                                             THEN m.credit - m.debit ELSE 0 END), 0)
                               AS closing_balance"""));
        }

        @Test
        @DisplayName("takes every filter for the two totals and the count")
        void totalsFollowTheFilters() {
            String sql = EmployeeStatementQuery.summarySql();
            String shown = "m.movement_date BETWEEN ? AND ? AND "
                    + EmployeeStatementQuery.rowFilterSql("m");
            int occurrences = sql.split(java.util.regex.Pattern.quote(shown), -1).length - 1;
            assertEquals(3, occurrences,
                    "the debit total, the credit total and the count each carry it: " + sql);
        }

        @Test
        @DisplayName("counts the matched rows so the pager has a last page to clamp to")
        void countsRows() {
            assertTrue(EmployeeStatementQuery.summarySql().contains("AS shown_count"));
        }

        @Test
        @DisplayName("binds forty-five values, in the order the repository adds them")
        void parameterCount() {
            // from, then (from + to + 12) three times, then to, then the employee
            assertEquals(45, parameters(EmployeeStatementQuery.summarySql()));
        }
    }

    @Nested
    @DisplayName("the scalar reads")
    class Scalars {

        @Test
        @DisplayName("the current balance comes from the view, never summed a second time")
        void balanceIsNotRecomputed() {
            assertTrue(EmployeeStatementQuery.CURRENT_BALANCE_SQL.contains("FROM employee_balance"),
                    "a second computation of a balance is the defect view_customer_receivables "
                            + "carried for years: " + EmployeeStatementQuery.CURRENT_BALANCE_SQL);
            assertEquals(1, parameters(EmployeeStatementQuery.CURRENT_BALANCE_SQL));
        }

        @Test
        @DisplayName("the earliest movement is one scalar read, not a loaded account")
        void earliest() {
            assertTrue(EmployeeStatementQuery.EARLIEST_MOVEMENT_SQL.startsWith("SELECT MIN("));
            assertEquals(1, parameters(EmployeeStatementQuery.EARLIEST_MOVEMENT_SQL));
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("a ledger row carries no treasury - the ledger holds no cash")
        void ledgerHasNoTreasury() {
            String sql = EmployeeStatementQuery.INSERT_LEDGER_SQL;
            assertFalse(sql.contains("treasury"),
                    "a row of employee_ledger carrying cash is a defect, not a feature: " + sql);
            assertEquals(6, parameters(sql));
        }

        @Test
        @DisplayName("there is no update of a recorded movement, only a delete")
        void noUpdate() {
            assertEquals(2, parameters(EmployeeStatementQuery.DELETE_LEDGER_SQL));
            assertTrue(EmployeeStatementQuery.DELETE_LEDGER_SQL
                            .contains("WHERE id = ? AND employee_id = ?"),
                    "scoped to the employee as well as the row, so a mistyped id cannot reach "
                            + "another employee's account");
        }

        @Test
        @DisplayName("the purpose is filed against the expense it belongs to")
        void purpose() {
            assertEquals(4, parameters(EmployeeStatementQuery.INSERT_PURPOSE_SQL));
            assertTrue(EmployeeStatementQuery.INSERT_PURPOSE_SQL
                    .contains("INSERT INTO employee_cash_purpose (expense_id, purpose"));
        }
    }

    @Test
    @DisplayName("every statement reads the view that unions the ledger with the cash")
    void oneSource() {
        for (String sql : new String[]{EmployeeStatementQuery.pageSql(),
                EmployeeStatementQuery.summarySql(), EmployeeStatementQuery.EARLIEST_MOVEMENT_SQL,
                EmployeeStatementQuery.USERS_SQL}) {
            assertTrue(sql.contains("employee_account_table"),
                    "the years of wage payments already in a customer's database are on this "
                            + "statement because it reads the table they were written to: " + sql);
        }
    }

    @Test
    @DisplayName("a narrowed filter changes no statement text - only what is bound")
    void filtersDoNotChangeTheSql() {
        assertEquals(EmployeeStatementQuery.pageSql(), EmployeeStatementQuery.pageSql());
        assertEquals(plain().kindList(), null);
        assertEquals("ADVANCE,DEDUCTION", narrowed().kindList(),
                "sorted, so two selections of the same kinds bind the same value");
    }
}
