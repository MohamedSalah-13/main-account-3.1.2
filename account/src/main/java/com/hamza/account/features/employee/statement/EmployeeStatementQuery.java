package com.hamza.account.features.employee.statement;

/**
 * Every statement over one employee's account, in one place and pinned character for character by
 * {@code EmployeeStatementQueryTest}.
 * <p>
 * It reads {@code employee_account_table} — the view that unions {@code employee_ledger} with the
 * {@code expenses_details} rows carrying an {@code emp_id}. So the years of wage payments already
 * in customers' databases appear on this statement with no data migration at all: it reads the
 * table they were written to.
 * <p>
 * Four properties hold here, and each is the same rule the party statement carries:
 * <ul>
 *   <li><b>The running balance is accumulated in SQL</b>, over every movement of the period,
 *       seeded with one scalar read of what came before it. A total restarted at zero prints a
 *       statement for September as though the employee began September owed nothing.</li>
 *   <li><b>The other filters sit in an outer query over the CTE.</b> A running balance that skips
 *       the rows a filter hid is not a balance: filter to deductions only, and each row would
 *       report a figure that ignores the pay between them.</li>
 *   <li><b>The page, the summary and the print extract share one {@code WHERE}</b>
 *       ({@link #rowFilterSql}), so the totals and the exported file cannot describe a different
 *       set from the table.</li>
 *   <li><b>No user value is concatenated.</b> Every filter is {@code ? IS NULL OR …}, which keeps
 *       the statement a constant a test can pin and keeps each value bound.</li>
 * </ul>
 */
public final class EmployeeStatementQuery {

    /** The view both halves of the statement come from. */
    private static final String VIEW = "employee_account_table";

    /**
     * How a period's rows are ordered, and the order the running balance accumulates in.
     * <p>
     * {@code entered_at} breaks a tie between two movements on one day, and {@code source_id} the
     * tie between two rows entered in the same second. Without a total order the window function
     * is free to accumulate two rows either way round, and a statement printed twice would show
     * two different running balances on the same two lines.
     */
    private static final String MOVEMENT_ORDER =
            "m.movement_date, m.entered_at, m.source, m.source_id";

    private EmployeeStatementQuery() {
    }

    /**
     * One page of the statement, each row carrying the balance after it.
     * <p>
     * Parameters in order: the employee and {@code from} for the opening seed, the employee again,
     * {@code from} and {@code to} for the period, then the twelve of {@link #rowFilterSql}, then
     * the limit and the offset — nineteen.
     */
    public static String pageSql() {
        return """
                WITH period AS (
                    SELECT m.employee_id,
                           m.movement_date,
                           m.entry_kind,
                           m.source,
                           m.source_id,
                           m.credit,
                           m.debit,
                           m.notes,
                           m.entered_at,
                           m.user_id,
                           COALESCE((SELECT SUM(p.credit - p.debit)
                                     FROM %1$s p
                                     WHERE p.employee_id = ?
                                       AND p.movement_date < ?), 0)
                           + SUM(m.credit - m.debit) OVER (
                               ORDER BY %2$s
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                             ) AS running_balance
                    FROM %1$s m
                    WHERE m.employee_id = ?
                      AND m.movement_date BETWEEN ? AND ?
                )
                SELECT m.movement_date,
                       m.entry_kind,
                       m.source,
                       m.source_id,
                       m.credit,
                       m.debit,
                       m.notes,
                       m.entered_at,
                       m.user_id,
                       COALESCE(u.user_name, '') AS user_name,
                       m.running_balance
                FROM period m
                         LEFT JOIN users u ON u.id = m.user_id
                WHERE %3$s
                ORDER BY m.movement_date DESC, m.entered_at DESC, m.source_id DESC
                LIMIT ? OFFSET ?"""
                .formatted(VIEW, MOVEMENT_ORDER, rowFilterSql("m"));
    }

    /**
     * The opening balance, the shown rows' totals, and the closing balance, in one row.
     * <p>
     * The two balances take the dates alone; the two totals take every filter. That line is the
     * first thing a later filter will get wrong, so it is stated here and pinned in the test.
     * <p>
     * The totals are summed as the two columns a reader sees rather than as one signed change,
     * because a sum cannot be taken in Java over rows that were never fetched — which means
     * {@link EmployeeStatementRow#debit()} and {@code credit()} are restated in SQL here. Two
     * statements of one rule is the shape of defect this package exists to remove, and <b>no unit
     * test can hold them together</b>: one side is Java and the other is text handed to MySQL.
     * {@code EmployeeStatementDatabaseAcceptanceTest} is what compares them, and it is gated.
     * <p>
     * <b>The count is taken here rather than guessed by the pager.</b> A page control that does
     * not know how many rows there are can only offer "next" until it stops, which is what makes a
     * typed page number impossible - and {@code PageJumpBox} needs a last page to clamp to.
     * <p>
     * Parameters in order: {@code from} for the opening sum; {@code from}, {@code to} and the
     * twelve row filters for the debit total; the same fourteen again for the credit total; and
     * again for the count; {@code to} for the closing sum; and the employee last — forty-five.
     */
    public static String summarySql() {
        String shown = "m.movement_date BETWEEN ? AND ? AND " + rowFilterSql("m");
        return """
                SELECT COALESCE(SUM(CASE WHEN m.movement_date < ?
                                         THEN m.credit - m.debit ELSE 0 END), 0)
                           AS opening_balance,
                       COALESCE(SUM(CASE WHEN %2$s THEN m.debit ELSE 0 END), 0)
                           AS total_debit,
                       COALESCE(SUM(CASE WHEN %2$s THEN m.credit ELSE 0 END), 0)
                           AS total_credit,
                       COALESCE(SUM(CASE WHEN %2$s THEN 1 ELSE 0 END), 0)
                           AS shown_count,
                       COALESCE(SUM(CASE WHEN m.movement_date <= ?
                                         THEN m.credit - m.debit ELSE 0 END), 0)
                           AS closing_balance
                FROM %1$s m
                WHERE m.employee_id = ?"""
                .formatted(VIEW, shown);
    }

    /**
     * The employee's first movement, which is where a statement opens by default.
     * <p>
     * Asked of the database rather than worked out from a loaded list: the alternative is reading
     * the whole account to learn one date, which is what the party screen used to do. Answers
     * {@code null} for an employee nothing has happened to yet.
     */
    public static final String EARLIEST_MOVEMENT_SQL =
            "SELECT MIN(m.movement_date) AS earliest FROM " + VIEW + " m WHERE m.employee_id = ?";

    /**
     * What one employee is owed right now — the whole history summed, with no period at all.
     * <p>
     * Read from {@code employee_balance} rather than summed again here: that view is derived from
     * this same statement's view, so the figure on the list and the figure under the statement
     * cannot drift. A second computation of a balance is the defect the party work spent a month
     * removing.
     */
    public static final String CURRENT_BALANCE_SQL =
            "SELECT COALESCE(b.balance, 0) AS balance FROM employee_balance b WHERE b.employee_id = ?";

    /** Who has entered a movement on this employee's account - the statement's user filter. */
    public static final String USERS_SQL = """
            SELECT DISTINCT m.user_id, COALESCE(u.user_name, '') AS user_name
            FROM employee_account_table m
                     LEFT JOIN users u ON u.id = m.user_id
            WHERE m.employee_id = ?
            ORDER BY user_name""";

    /**
     * The filters that hide rows, shared by the page and by the summary so the two cannot come to
     * describe different sets.
     * <p>
     * Each is {@code ? IS NULL OR …}, so one statement serves a filter that is set and one that is
     * not. The kinds are matched with {@code FIND_IN_SET} against a bound comma-joined list for
     * the same reason: an {@code IN (?, ?, ?)} whose length follows the selection would be a
     * different statement per selection and nothing could pin any of them.
     * <p>
     * The amount is compared as {@code credit + debit} — exactly one of the two is ever non-zero,
     * so their sum is the movement's magnitude without a {@code CASE} or an {@code ABS} over a
     * signed expression.
     * <p>
     * The text search escapes its wildcards with {@code ESCAPE '!'} (the {@code MasterDataQuery}
     * rule), so a note containing a {@code %} is searchable rather than matching every row.
     * <p>
     * Twelve parameters: kinds ×2, source ×2, user ×2, minimum ×2, maximum ×2, text ×2.
     *
     * @param alias what the calling statement calls the view
     */
    static String rowFilterSql(String alias) {
        return """
                (? IS NULL OR FIND_IN_SET(%1$s.entry_kind, ?))
                      AND (? IS NULL OR %1$s.source = ?)
                      AND (? IS NULL OR %1$s.user_id = ?)
                      AND (? IS NULL OR (%1$s.credit + %1$s.debit) >= ?)
                      AND (? IS NULL OR (%1$s.credit + %1$s.debit) <= ?)
                      AND (? IS NULL OR %1$s.notes LIKE ? ESCAPE '!')"""
                .formatted(alias);
    }

    /** A contains-match with the wildcards a person may legitimately type escaped out of it. */
    public static String pattern(String text) {
        String value = text == null ? "" : text.strip();
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    // ---- writing the ledger -------------------------------------------------------------

    /**
     * A hand-entered movement. {@code amount} is unsigned and the direction is the kind's — see
     * {@code EmployeeEntryKind}, and the CHECK in V58 that refuses anything else.
     */
    public static final String INSERT_LEDGER_SQL = """
            INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?)""";

    /**
     * Removing one. There is deliberately no update: a recorded movement is corrected with an
     * opposing entry, not edited - {@code docs/employees-plan.md} §11, and the decision the party
     * screens enforce by silence while their code assumes otherwise.
     */
    public static final String DELETE_LEDGER_SQL =
            "DELETE FROM employee_ledger WHERE id = ? AND employee_id = ?";

    /** What a payroll run wrote, which is the one thing a hand may not remove. */
    public static final String LEDGER_RUN_SQL =
            "SELECT payroll_run_id FROM employee_ledger WHERE id = ? AND employee_id = ?";

    // ---- the purpose beside a payment ---------------------------------------------------

    public static final String INSERT_PURPOSE_SQL = """
            INSERT INTO employee_cash_purpose (expense_id, purpose, payroll_run_id, user_id)
            VALUES (?, ?, ?, ?)""";

    public static final String PURPOSE_SQL =
            "SELECT purpose FROM employee_cash_purpose WHERE expense_id = ?";
}
