package com.hamza.account.features.delegate;

/**
 * Every statement over the commission run, its lines and their postings - pinned character for
 * character by {@code CommissionRunQueryTest}, with each one's parameter count.
 */
public final class CommissionRunQuery {

    /** {@code active_key}: year * 100 + month while the run is APPROVED - the unique key of "this month's run". */
    public static final String ACTIVE_RUN_ID_SQL = """
            SELECT id
            FROM commission_run
            WHERE active_key = ?""";

    public static final String INSERT_RUN_SQL = """
            INSERT INTO commission_run (period_year, period_month, notes, user_id)
            VALUES (?, ?, ?, ?)""";

    public static final String INSERT_LINE_SQL = """
            INSERT INTO commission_line (run_id, employee_id, rule_id, basis, tier_mode, target, tiers_snapshot,
                   sales, sales_returns, collected, base_amount, achievement_percent, tier, rate_percent, amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /** Newest month first; a cancelled run stays listed, under the one that replaced it. */
    public static final String RUNS_SQL = """
            SELECT r.id, r.period_year, r.period_month, r.status, r.notes, r.approved_at, r.cancel_reason,
                   u.user_name,
                   COUNT(l.id)                AS line_count,
                   COALESCE(SUM(l.amount), 0) AS total,
                   COUNT(p.line_id)           AS posted_lines
            FROM commission_run r
                     JOIN users u ON u.id = r.user_id
                     LEFT JOIN commission_line l ON l.run_id = r.id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            GROUP BY r.id, r.period_year, r.period_month, r.status, r.notes, r.approved_at, r.cancel_reason,
                     u.user_name
            ORDER BY r.period_year DESC, r.period_month DESC, r.id DESC""";

    public static final String RUN_SQL = RUNS_SQL.replace("GROUP BY", "WHERE r.id = ?\nGROUP BY");

    public static final String LINES_SQL = """
            SELECT l.id, l.run_id, l.employee_id, e.column_name, l.rule_id, l.basis, l.tier_mode, l.target,
                   l.tiers_snapshot, l.sales, l.sales_returns, l.collected, l.base_amount,
                   l.achievement_percent, l.tier, l.rate_percent, l.amount,
                   p.payroll_run_id, p.ledger_entry_id
            FROM commission_line l
                     JOIN employees e ON e.id = l.employee_id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            WHERE l.run_id = ?
            ORDER BY e.column_name""";

    /**
     * One delegate's approved months, newest first - his commission statement. A cancelled run
     * is not on it: it was withdrawn, and the month's approved figure is the one that replaced it.
     */
    public static final String EMPLOYEE_LINES_SQL = """
            SELECT r.period_year, r.period_month,
                   l.id, l.run_id, l.employee_id, e.column_name, l.rule_id, l.basis, l.tier_mode, l.target,
                   l.tiers_snapshot, l.sales, l.sales_returns, l.collected, l.base_amount,
                   l.achievement_percent, l.tier, l.rate_percent, l.amount,
                   p.payroll_run_id, p.ledger_entry_id
            FROM commission_line l
                     JOIN commission_run r ON r.id = l.run_id
                     JOIN employees e ON e.id = l.employee_id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            WHERE l.employee_id = ?
              AND r.status = 'APPROVED'
            ORDER BY r.period_year DESC, r.period_month DESC
            LIMIT ?""";

    /**
     * The status the caller read is in the WHERE, so two people cancelling at once give one
     * cancellation and one refusal. The trigger refuses it as well when anything was posted.
     */
    public static final String CANCEL_RUN_SQL = """
            UPDATE commission_run
            SET status = 'CANCELLED', cancelled_at = NOW(), cancelled_by = ?, cancel_reason = ?
            WHERE id = ?
              AND status = 'APPROVED'""";

    // ---- posting: once, by one road ------------------------------------------------------------

    /**
     * The lines of a run that may still be posted to the delegates' accounts. Locked: the payroll
     * may be consuming the same lines on another till, and whoever inserts the posting second
     * meets the primary key.
     */
    public static final String UNPOSTED_LINES_SQL = """
            SELECT l.id, l.employee_id, l.amount
            FROM commission_line l
                     JOIN commission_run r ON r.id = l.run_id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            WHERE l.run_id = ?
              AND r.status = 'APPROVED'
              AND l.amount > 0
              AND p.line_id IS NULL
            FOR UPDATE OF l""";

    /** A commission approved by a run - the one kind of ledger entry nobody may type. */
    public static final String INSERT_LEDGER_COMMISSION_SQL = """
            INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes, user_id)
            VALUES (?, ?, 'COMMISSION', ?, ?, ?)""";

    public static final String INSERT_ACCOUNT_POSTING_SQL = """
            INSERT INTO commission_posting (line_id, ledger_entry_id, user_id)
            VALUES (?, ?, ?)""";

    /**
     * What the payroll of a period owes one delegate in commission: every approved line not yet
     * posted, <b>of that period or an earlier one</b>. October's commission is approved in
     * November, so a shop paying it with November's salary and one paying it with October's
     * (run late) both find it; what has been paid is never found twice.
     */
    public static final String PAYROLL_DUE_SQL = """
            SELECT COALESCE(SUM(l.amount), 0)
            FROM commission_line l
                     JOIN commission_run r ON r.id = l.run_id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            WHERE r.status = 'APPROVED'
              AND p.line_id IS NULL
              AND l.amount > 0
              AND l.employee_id = ?
              AND r.period_year * 100 + r.period_month <= ?""";

    /** The same lines, the same WHERE, written as postings of one payroll run. */
    public static final String INSERT_PAYROLL_POSTINGS_SQL = """
            INSERT INTO commission_posting (line_id, payroll_run_id, user_id)
            SELECT l.id, ?, ?
            FROM commission_line l
                     JOIN commission_run r ON r.id = l.run_id
                     LEFT JOIN commission_posting p ON p.line_id = l.id
            WHERE r.status = 'APPROVED'
              AND p.line_id IS NULL
              AND l.amount > 0
              AND l.employee_id = ?
              AND r.period_year * 100 + r.period_month <= ?""";

    // ---- what a frozen month forbids -------------------------------------------------------------

    /**
     * Whether a run - cancelled ones included - has a line computed under the rule of that day.
     * Such a rule is history: amending it in place would make the line's {@code rule_id} point at
     * terms the month was not judged by.
     */
    public static final String RULE_OF_DAY_USED_SQL = """
            SELECT COUNT(*)
            FROM commission_line l
                     JOIN employee_commission_rule r ON r.id = l.rule_id
            WHERE r.employee_id = ?
              AND r.effective_from = ?""";

    public static final String RULE_USED_SQL = """
            SELECT COUNT(*)
            FROM commission_line
            WHERE rule_id = ?""";

    private CommissionRunQuery() {
    }
}
