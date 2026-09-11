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
                WITH period AS (
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
                           COALESCE((SELECT SUM(p.purchase - p.discount - p.paid)
                                     FROM account_customer_table p
                                     WHERE p.account_code = ?
                                       AND p.account_date < ?), 0)
                           + SUM(m.purchase - m.discount - m.paid) OVER (
                               ORDER BY m.account_date, m.created_at, m.information, m.account_num
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                             ) AS running_balance
                    FROM account_customer_table m
                    WHERE m.account_code = ?
                      AND m.account_date BETWEEN ? AND ?
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
                       m.running_balance
                FROM period m
                         LEFT JOIN treasury t ON t.id = m.treasury_id
                         LEFT JOIN users u ON u.id = m.user_id
                WHERE (? IS NULL OR FIND_IN_SET(m.information, ?))
                      AND (? IS NULL OR m.treasury_id = ?)
                      AND (? IS NULL OR m.user_id = ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) >= ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) <= ?)
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
                SELECT COALESCE(SUM(CASE WHEN m.account_date < ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS opening_balance,
                       COALESCE(SUM(CASE WHEN m.account_date BETWEEN ? AND ? AND (? IS NULL OR FIND_IN_SET(m.information, ?))
                      AND (? IS NULL OR m.treasury_id = ?)
                      AND (? IS NULL OR m.user_id = ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) >= ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) <= ?)
                      AND (? IS NULL OR m.notes LIKE ? ESCAPE '!'
                           OR CAST(m.numberInv AS CHAR) = ?
                           OR CAST(m.account_num AS CHAR) = ?)
                      AND (? = FALSE OR m.type = 2)
                                         THEN GREATEST(m.purchase - m.discount, 0)
                                              + GREATEST(-m.paid, 0) ELSE 0 END), 0)
                           AS total_debit,
                       COALESCE(SUM(CASE WHEN m.account_date BETWEEN ? AND ? AND (? IS NULL OR FIND_IN_SET(m.information, ?))
                      AND (? IS NULL OR m.treasury_id = ?)
                      AND (? IS NULL OR m.user_id = ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) >= ?)
                      AND (? IS NULL OR ABS(m.purchase - m.discount - m.paid) <= ?)
                      AND (? IS NULL OR m.notes LIKE ? ESCAPE '!'
                           OR CAST(m.numberInv AS CHAR) = ?
                           OR CAST(m.account_num AS CHAR) = ?)
                      AND (? = FALSE OR m.type = 2)
                                         THEN GREATEST(m.paid, 0)
                                              + GREATEST(-(m.purchase - m.discount), 0) ELSE 0 END), 0)
                           AS total_credit,
                       COALESCE(SUM(CASE WHEN m.account_date <= ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS closing_balance
                FROM account_customer_table m
                WHERE m.account_code = ?""", PartyStatementQuery.summarySql(PartyKind.CUSTOMER));
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
     * The summary binds: {@code from}, then the period and the fifteen filters twice over
     * for the two conditional totals, then {@code to}, then the party.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theSummaryBindsTheValuesTheRepositorySupplies(PartyKind kind) {
        assertEquals(1 + 2 * (2 + ROW_FILTER_PARAMETERS) + 1 + 1,
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
        assertEquals(2, countOccurrences(PartyStatementQuery.summarySql(kind), filter),
                "each of the summary's two period totals must use the same shared row filter");
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
        assertTrue(sql.contains("""
                COALESCE(SUM(CASE WHEN m.account_date < ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS opening_balance"""),
                "the opening balance must be dates-only");
        assertTrue(sql.contains("""
                COALESCE(SUM(CASE WHEN m.account_date <= ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS closing_balance"""),
                "the closing balance must be dates-only");
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
