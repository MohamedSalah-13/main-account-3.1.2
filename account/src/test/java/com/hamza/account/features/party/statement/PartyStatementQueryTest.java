package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every statement of the party statement, the way {@code DocumentDaoStatementsTest}
 * and {@code PartyLedgerStatementsTest} pin theirs.
 * <p>
 * The reason is the one those tests give: a merge that swaps two adjacent columns still
 * produces valid SQL. It just reads the discount as a treasury id, and the first person to
 * notice is a customer holding a statement. Nothing else in the build can see that, because
 * the column names are strings on both sides.
 * <p>
 * The second thing pinned here is the <b>parameter count</b> of each statement against what
 * the repository binds. A query whose placeholders and values have drifted by one does not
 * fail loudly — it shifts every value along by one position and answers something.
 */
class PartyStatementQueryTest {

    /** What {@link PartyStatementQuery#rowFilterSql} binds, and therefore what the repository must supply. */
    private static final int ROW_FILTER_PARAMETERS = 15;

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    @Test
    @DisplayName("the customer page, character for character")
    void theCustomerPageStatement() {
        assertEquals("""
                WITH prior AS (
                    SELECT COALESCE(SUM(p.purchase - p.discount - p.paid), 0) AS balance,
                           COALESCE(SUM(p.purchase_own - p.discount_own - p.paid_own), 0) AS balance_own
                    FROM account_customer_table p
                    WHERE p.account_code = ?
                      AND p.account_date < ?
                ),
                period AS (
                    SELECT m.account_num,
                           m.account_code,
                           m.account_date,
                           m.created_at,
                           m.information,
                           m.type,
                           m.purchase,
                           m.discount,
                           m.paid,
                           m.treasury_id,
                           m.user_id,
                           m.numberInv,
                           m.notes,
                           m.purchase_own,
                           m.discount_own,
                           m.paid_own,
                           prior.balance + SUM(m.purchase - m.discount - m.paid) OVER running
                               AS running_balance,
                           prior.balance_own + SUM(m.purchase_own - m.discount_own - m.paid_own) OVER running
                               AS running_balance_own
                    FROM account_customer_table m
                             CROSS JOIN prior
                    WHERE m.account_code = ?
                      AND m.account_date BETWEEN ? AND ?
                    WINDOW running AS (ORDER BY m.account_date, m.created_at, m.information, m.account_num ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                )
                SELECT m.account_num,
                       m.account_code,
                       m.account_date,
                       m.created_at,
                       m.information,
                       m.type,
                       m.purchase,
                       m.discount,
                       m.paid,
                       m.treasury_id,
                       COALESCE(t.t_name, '') AS treasury_name,
                       m.user_id,
                       COALESCE(u.user_name, '') AS user_name,
                       m.numberInv,
                       m.notes,
                       m.running_balance,
                       m.purchase_own,
                       m.discount_own,
                       m.paid_own,
                       m.running_balance_own
                FROM period m
                         LEFT JOIN treasury t ON t.id = m.treasury_id
                         LEFT JOIN users u ON u.id = m.user_id
                WHERE (? IS NULL OR FIND_IN_SET(m.information, ?))
                      AND (? IS NULL OR m.treasury_id = ?)
                      AND (? IS NULL OR m.user_id = ?)
                      AND (? IS NULL OR ABS(m.purchase_own - m.discount_own - m.paid_own) >= ?)
                      AND (? IS NULL OR ABS(m.purchase_own - m.discount_own - m.paid_own) <= ?)
                      AND (? IS NULL OR m.notes LIKE ? ESCAPE '!'
                           OR CAST(m.numberInv AS CHAR) = ?
                           OR CAST(m.account_num AS CHAR) = ?)
                      AND (? = FALSE OR m.type = 2)
                ORDER BY m.account_date DESC, m.created_at DESC,
                         m.information DESC, m.account_num DESC
                LIMIT ? OFFSET ?""", PartyStatementQuery.pageSql(PartyKind.CUSTOMER));
    }

    @Test
    @DisplayName("the customer summary, character for character")
    void theCustomerSummaryStatement() {
        assertEquals("""
                SELECT COALESCE(SUM(CASE WHEN c.brought_forward THEN c.change_base ELSE 0 END), 0)
                           AS opening_balance,
                       COALESCE(SUM(CASE WHEN c.listed THEN c.debit_base ELSE 0 END), 0) AS total_debit,
                       COALESCE(SUM(CASE WHEN c.listed THEN c.credit_base ELSE 0 END), 0) AS total_credit,
                       COALESCE(SUM(CASE WHEN c.closing THEN c.change_base ELSE 0 END), 0)
                           AS closing_balance,
                       COALESCE(SUM(CASE WHEN c.brought_forward THEN c.change_own ELSE 0 END), 0)
                           AS opening_balance_own,
                       COALESCE(SUM(CASE WHEN c.listed THEN c.debit_own ELSE 0 END), 0) AS total_debit_own,
                       COALESCE(SUM(CASE WHEN c.listed THEN c.credit_own ELSE 0 END), 0) AS total_credit_own,
                       COALESCE(SUM(CASE WHEN c.closing THEN c.change_own ELSE 0 END), 0)
                           AS closing_balance_own
                FROM (SELECT m.account_date < ? AS brought_forward,
                             (m.account_date BETWEEN ? AND ? AND (? IS NULL OR FIND_IN_SET(m.information, ?))
                      AND (? IS NULL OR m.treasury_id = ?)
                      AND (? IS NULL OR m.user_id = ?)
                      AND (? IS NULL OR ABS(m.purchase_own - m.discount_own - m.paid_own) >= ?)
                      AND (? IS NULL OR ABS(m.purchase_own - m.discount_own - m.paid_own) <= ?)
                      AND (? IS NULL OR m.notes LIKE ? ESCAPE '!'
                           OR CAST(m.numberInv AS CHAR) = ?
                           OR CAST(m.account_num AS CHAR) = ?)
                      AND (? = FALSE OR m.type = 2)) AS listed,
                             m.account_date <= ? AS closing,
                             m.purchase - m.discount - m.paid AS change_base,
                             GREATEST(m.purchase - m.discount, 0) + GREATEST(-m.paid, 0) AS debit_base,
                             GREATEST(m.paid, 0) + GREATEST(-(m.purchase - m.discount), 0) AS credit_base,
                             m.purchase_own - m.discount_own - m.paid_own AS change_own,
                             GREATEST(m.purchase_own - m.discount_own, 0) + GREATEST(-m.paid_own, 0)
                                 AS debit_own,
                             GREATEST(m.paid_own, 0) + GREATEST(-(m.purchase_own - m.discount_own), 0)
                                 AS credit_own
                      FROM account_customer_table m
                      WHERE m.account_code = ?) c""", PartyStatementQuery.summarySql(PartyKind.CUSTOMER));
    }

    /**
     * The supplier's statements are the customer's over the other view. Asserting the one
     * difference, rather than repeating ninety lines, is what keeps the two from being
     * allowed to diverge quietly — the property {@link PartyLedgerSpec} exists to hold.
     */
    @Test
    @DisplayName("the supplier's statements differ from the customer's only in the view")
    void theSupplierStatementsAreTheSameOverTheOtherView() {
        assertEquals(
                PartyStatementQuery.pageSql(PartyKind.CUSTOMER)
                        .replace("account_customer_table", "account_suppliers_table"),
                PartyStatementQuery.pageSql(PartyKind.SUPPLIER));
        assertEquals(
                PartyStatementQuery.summarySql(PartyKind.CUSTOMER)
                        .replace("account_customer_table", "account_suppliers_table"),
                PartyStatementQuery.summarySql(PartyKind.SUPPLIER));
    }

    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theEarliestMovementStatement(PartyKind kind) {
        assertEquals("SELECT MIN(m.account_date) AS earliest FROM "
                        + PartyLedgerSpec.of(kind).view() + " m WHERE m.account_code = ?",
                PartyStatementQuery.earliestMovementSql(kind));
        assertEquals(1, placeholders(PartyStatementQuery.earliestMovementSql(kind)));
    }

    /**
     * The balance a printed invoice carries: the running balance on its own row, over the
     * same order the statement accumulates in - so paper and statement cannot disagree.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theBalanceAfterAMovementStatement(PartyKind kind) {
        assertEquals("""
                        SELECT r.running_balance
                        FROM (SELECT m.information,
                                     m.account_num,
                                     SUM(m.purchase - m.discount - m.paid) OVER (
                                         ORDER BY m.account_date, m.created_at, m.information, m.account_num
                                         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                                     ) AS running_balance
                              FROM %s m
                              WHERE m.account_code = ?) r
                        WHERE r.information = ?
                          AND r.account_num = ?""".formatted(PartyLedgerSpec.of(kind).view()),
                PartyStatementQuery.balanceAfterMovementSql(kind));
        assertEquals(3, placeholders(PartyStatementQuery.balanceAfterMovementSql(kind)));
    }

    /**
     * The page binds: the party and {@code from} for the opening seed, the party again,
     * {@code from} and {@code to}, the fifteen row filters, then the limit and the offset.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void thePageBindsTheValuesTheRepositorySupplies(PartyKind kind) {
        assertEquals(2 + 3 + ROW_FILTER_PARAMETERS + 2,
                placeholders(PartyStatementQuery.pageSql(kind)));
    }

    /**
     * The summary binds: {@code from} for what is brought forward, then the period and the fifteen
     * filters once for what is listed, then {@code to}, then the party. Each movement is classified
     * once and summed in both currencies (V82), so the filter is not bound once per total any more.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theSummaryBindsTheValuesTheRepositorySupplies(PartyKind kind) {
        assertEquals(1 + 2 + ROW_FILTER_PARAMETERS + 1 + 1,
                placeholders(PartyStatementQuery.summarySql(kind)));
    }

    /**
     * <b>The page and the summary are narrowed by the same clause.</b> This is the
     * {@code ItemsDao.catalogQuery} rule: build the rows and their total from two
     * {@code WHERE}s and the footer starts describing a different set from the table, with
     * nothing on the page to say which is right. Here the totals and the exported file come
     * from the same text that selects the rows.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theRowsAndTheirTotalsAreNarrowedByTheSameClause(PartyKind kind) {
        String filter = PartyStatementQuery.rowFilterSql("m");
        assertTrue(PartyStatementQuery.pageSql(kind).contains(filter),
                "the page must use the shared row filter");
        assertEquals(1, countOccurrences(PartyStatementQuery.summarySql(kind), filter),
                "the summary's listed rows must be chosen by the same shared row filter, once");
    }

    /**
     * The balances are narrowed by the dates and by nothing else.
     * <p>
     * Pinned as text because it is the rule a later filter is most likely to break, and
     * breaking it produces a plausible-looking number rather than an error: a "balance before
     * the period" computed over payments only is not anybody's balance, and it is the figure
     * a customer is asked to agree with.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theTwoBalancesAnswerTheDatesAlone(PartyKind kind) {
        String sql = PartyStatementQuery.summarySql(kind);
        assertTrue(sql.contains("m.account_date < ? AS brought_forward,"),
                "what is brought forward must be chosen by the date alone");
        assertTrue(sql.contains("m.account_date <= ? AS closing,"),
                "the closing balance must be chosen by the date alone");
        for (String balance : new String[]{"opening_balance", "closing_balance"}) {
            String flag = balance.startsWith("opening") ? "c.brought_forward" : "c.closing";
            assertTrue(sql.contains("COALESCE(SUM(CASE WHEN " + flag + " THEN c.change_base ELSE 0 END), 0)\n"
                            + "           AS " + balance),
                    balance + " must sum what the date chose, and nothing a filter narrowed");
            assertTrue(sql.contains("COALESCE(SUM(CASE WHEN " + flag + " THEN c.change_own ELSE 0 END), 0)\n"
                            + "           AS " + balance + "_own"),
                    balance + " in the party's own currency must answer the same question");
        }
    }

    /** Only identifiers the specification owns reach the SQL; a filter is always bound. */
    @Test
    void noUserValueIsSplicedIntoAStatement() {
        PartyStatementFilter filter = new PartyStatementFilter(PartyKind.CUSTOMER, 7,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Set.of(PartyMovementKind.PAYMENT), 3, 2,
                new BigDecimal("10"), new BigDecimal("999"), "'; DROP TABLE custom; --", true,
                0, 50);
        String sql = PartyStatementQuery.pageSql(filter.partyKind());
        assertFalse(sql.contains("DROP TABLE"), "the search text must never reach the statement");
        assertFalse(sql.contains("999"), "an amount must never reach the statement");
        assertFalse(sql.contains(" = 7"), "the party id must never reach the statement");
    }

    /** The wildcard escape that makes a note containing a {@code %} searchable. */
    @Test
    void theTextPatternEscapesItsWildcards() {
        assertEquals("%100!% off%", PartyStatementQuery.pattern("100% off"));
        assertEquals("%a!_b%", PartyStatementQuery.pattern("a_b"));
        assertEquals("%!!%", PartyStatementQuery.pattern("!"));
        assertEquals("%%", PartyStatementQuery.pattern("   "));
        assertEquals("%%", PartyStatementQuery.pattern(null));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
