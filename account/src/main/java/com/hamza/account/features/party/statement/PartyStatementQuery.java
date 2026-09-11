package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

/**
 * Every statement over one party's ledger, built from {@link PartyLedgerSpec} and from
 * nothing a caller supplies.
 * <p>
 * <b>This package answers one question: what has this party done, and what do they owe.</b>
 * Every statement here is scoped to a single {@code account_code}. The other question —
 * what does every party owe — is a different one with a different answer
 * ({@code account_customer_totals}), and keeping them apart is what lets the running
 * balance below be seeded with one scalar read instead of a correlated subquery per row.
 * <p>
 * <b>Why the SQL lives here and not in the repository.</b> It is pinned character for
 * character by {@code PartyStatementQueryTest}, the way {@code DocumentDaoStatementsTest}
 * and {@code PartyLedgerStatementsTest} pin theirs. A merge that swaps two adjacent
 * columns still produces valid SQL — it just reads the discount as a treasury id — so the
 * pinning is the only thing standing between that and a customer's statement.
 * <p>
 * <b>Only identifiers the specification owns are concatenated; every user value is bound.</b>
 * The view name and the column names come from {@link PartyLedgerSpec}, which checks each
 * against an identifier pattern in its own constructor. That is the property
 * {@code MasterDataQuery} rests on too, and it is the one thing to preserve when adding a
 * filter here: a filter is a bound parameter, never text spliced into the statement.
 * <p>
 * <b>The page, the summary and the print extract are built from one {@code WHERE}.</b>
 * {@link #rowFilterSql(String)} is shared, so a filter cannot narrow the list on screen
 * and leave the totals — or the exported file — describing a different set. That is the
 * {@code ItemsDao.catalogQuery} lesson applied before it had a chance to bite here.
 */
public final class PartyStatementQuery {

    /** How a period's rows are ordered, and the order the running balance accumulates in. */
    private static final String MOVEMENT_ORDER =
            "m.account_date, m.created_at, m.information, m.account_num";

    private PartyStatementQuery() {
    }

    /**
     * One page of a party's statement, each row carrying the balance after it.
     * <p>
     * Two things about that running balance are deliberate, and both were absent before.
     * <p>
     * It is accumulated <b>in SQL</b> with a window function over {@link #MOVEMENT_ORDER},
     * and <b>seeded with what the party owed before {@code from}</b> — so the first row of
     * a period's statement starts from the real balance, not from zero. The screen this
     * replaces filtered a list in memory and restarted the running total at zero, so a
     * statement for September was printed as though the customer began September owing
     * nothing: defect خ-2 of {@code docs/party-plan.md}.
     * <p>
     * And it is accumulated over <b>every</b> movement of the period before the other
     * filters run, which is why those sit in an outer query over the CTE. A running
     * balance that skips the rows a filter hid is not a balance — filter to payments only
     * and each row would report a balance that ignores the invoices between them.
     * <p>
     * Parameters in order: the party and {@code from} for the opening seed, the party
     * again, {@code from} and {@code to} for the period, then the fifteen of
     * {@link #rowFilterSql(String)}, then the limit and the offset.
     */
    public static String pageSql(PartyKind kind) {
        String view = PartyLedgerSpec.of(kind).view();
        return """
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
                                     FROM %1$s p
                                     WHERE p.account_code = ?
                                       AND p.account_date < ?), 0)
                           + SUM(m.purchase - m.discount - m.paid) OVER (
                               ORDER BY %2$s
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                             ) AS running_balance
                    FROM %1$s m
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
                WHERE %3$s
                ORDER BY m.account_date DESC, m.created_at DESC,
                         m.information DESC, m.account_num DESC
                LIMIT ? OFFSET ?"""
                .formatted(view, MOVEMENT_ORDER, rowFilterSql("m"));
    }

    /**
     * The opening balance, the shown rows' totals, and the closing balance, in one row.
     * <p>
     * <b>The two balances answer the dates and nothing else; the two totals answer every
     * filter.</b> A balance is the sum of everything up to a day: narrowed by movement kind
     * or by till it is a number nobody owes, and it is the number a customer is asked to
     * sign for. {@code TreasuryStatements.SELECT_STATEMENT_SUMMARY} draws the same line for
     * the same reason, and {@code PartyStatementTest} pins this one — it is the first thing
     * a later filter will get wrong.
     * <p>
     * The totals are summed as the two columns a reader sees rather than as one signed
     * change: {@code debit} takes what the party was charged plus any cash handed back to
     * them, {@code credit} what they paid plus what they were credited. That is
     * {@link PartyStatementRow#debit()} and {@link PartyStatementRow#credit()} restated in
     * SQL, because a sum cannot be taken in Java over rows that were never fetched.
     * <p>
     * <b>Two statements of one rule is exactly the shape of defect this package was built to
     * remove, so say plainly what holds them together and what does not.</b> No unit test can:
     * one side is Java and the other is text handed to MySQL. Only
     * {@code PartyStatementViewAcceptanceTest} compares them, by summing the rows it fetched
     * in Java and asking the database for the same period — and that test is gated on
     * {@code -Daccount.db.acceptance=true}, so a green {@code mvn clean test} does not run it.
     * A gated test is not a passing test: run it after touching either side.
     * <p>
     * Parameters in order: {@code from} for the opening sum, then {@code from} and
     * {@code to} plus the fifteen row filters for the debit total, the same seventeen again
     * for the credit total, {@code to} for the closing sum, and the party last - thirty-seven
     * in all, which {@code PartyStatementQueryTest} counts against what the repository binds.
     */
    public static String summarySql(PartyKind kind) {
        String shown = "m.account_date BETWEEN ? AND ? AND " + rowFilterSql("m");
        return """
                SELECT COALESCE(SUM(CASE WHEN m.account_date < ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS opening_balance,
                       COALESCE(SUM(CASE WHEN %2$s
                                         THEN GREATEST(m.purchase - m.discount, 0)
                                              + GREATEST(-m.paid, 0) ELSE 0 END), 0)
                           AS total_debit,
                       COALESCE(SUM(CASE WHEN %2$s
                                         THEN GREATEST(m.paid, 0)
                                              + GREATEST(-(m.purchase - m.discount), 0) ELSE 0 END), 0)
                           AS total_credit,
                       COALESCE(SUM(CASE WHEN m.account_date <= ?
                                         THEN m.purchase - m.discount - m.paid ELSE 0 END), 0)
                           AS closing_balance
                FROM %1$s m
                WHERE m.account_code = ?"""
                .formatted(PartyLedgerSpec.of(kind).view(), shown);
    }

    /**
     * The party's first movement date, which is where a statement opens by default.
     * <p>
     * Asked of the database rather than worked out from a loaded list — which is what
     * {@code AccountDetailsController.miniDate} did, at the cost of reading the whole
     * ledger to learn one date. Every party carries at least the opening-balance row the
     * view synthesizes from their own record, so this answers null only for an id that
     * does not exist.
     */
    public static String earliestMovementSql(PartyKind kind) {
        return "SELECT MIN(m.account_date) AS earliest FROM "
                + PartyLedgerSpec.of(kind).view() + " m WHERE m.account_code = ?";
    }

    /**
     * What one party owes right now - their whole history summed, with no period at all.
     * <p>
     * It exists because two screens need that number and nothing else, and the cheapest thing
     * they had was to read the entire ledger of every party into memory and take the running
     * balance off the last row of the one they wanted ({@code Add_AccountController.getBalance},
     * which did it again on every change of the selected party). One scalar read answers it.
     */
    public static String currentBalanceSql(PartyKind kind) {
        return "SELECT COALESCE(SUM(m.purchase - m.discount - m.paid), 0) AS balance FROM "
                + PartyLedgerSpec.of(kind).view() + " m WHERE m.account_code = ?";
    }

    /**
     * The filters that hide rows, shared by the page and by the summary so that the two
     * cannot come to describe different sets.
     * <p>
     * Each is {@code ? IS NULL OR …}, so one statement serves a filter that is set and one
     * that is not, and the SQL stays a constant a test can pin. The kinds are matched with
     * {@code FIND_IN_SET} against a bound comma-joined list for that same reason: an
     * {@code IN (?, ?, ?)} whose length follows the user's selection would be a different
     * statement per selection, and nothing could pin any of them.
     * <p>
     * The text search escapes its wildcards with {@code ESCAPE '!'} — the
     * {@code MasterDataQuery.pattern} rule — so a note containing a {@code %} is searchable
     * rather than matching every row. It looks in the notes, in the document number and in
     * the movement number, because "find invoice 4312" and "find that note about the
     * cheque" are the same box to a user.
     * <p>
     * Fifteen parameters: kinds ×2, treasury ×2, user ×2, minimum ×2, maximum ×2, text ×4
     * (the null test, the pattern, and the two exact number comparisons), deferred ×1.
     *
     * @param alias what the calling statement calls the ledger
     */
    static String rowFilterSql(String alias) {
        return """
                (? IS NULL OR FIND_IN_SET(%1$s.information, ?))
                      AND (? IS NULL OR %1$s.treasury_id = ?)
                      AND (? IS NULL OR %1$s.user_id = ?)
                      AND (? IS NULL OR ABS(%1$s.purchase - %1$s.discount - %1$s.paid) >= ?)
                      AND (? IS NULL OR ABS(%1$s.purchase - %1$s.discount - %1$s.paid) <= ?)
                      AND (? IS NULL OR %1$s.notes LIKE ? ESCAPE '!'
                           OR CAST(%1$s.numberInv AS CHAR) = ?
                           OR CAST(%1$s.account_num AS CHAR) = ?)
                      AND (? = FALSE OR %1$s.type = 2)"""
                .formatted(alias);
    }

    /**
     * Every till, closed ones included.
     * <p>
     * A movement entered against a till that has since been closed still has to be
     * filterable — the same reason {@code Add_AccountController.selectTreasury} adds a
     * missing name back to its picker rather than silently showing the first one.
     */
    public static final String TREASURIES_SQL =
            "SELECT id, t_name, is_active FROM treasury ORDER BY sort_order, id";

    public static final String USERS_SQL =
            "SELECT id, user_name FROM users ORDER BY user_name, id";

    /** The wildcard-escaped pattern a text filter is bound as. */
    public static String pattern(String text) {
        String value = text == null ? "" : text.strip();
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
