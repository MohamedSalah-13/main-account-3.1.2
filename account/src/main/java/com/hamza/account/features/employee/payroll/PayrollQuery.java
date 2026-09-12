package com.hamza.account.features.employee.payroll;

/**
 * Every statement the payroll runs over, in one place and pinned by {@code PayrollQueryTest}.
 * <p>
 * The reason is the one {@code DocumentDaoStatementsTest} records: a merge that swaps two
 * adjacent columns still produces valid SQL - it just stores the deduction as the commission.
 * Pinning the text and the parameter count is the only thing between that and a customer's
 * payroll.
 */
public final class PayrollQuery {

    private PayrollQuery() {
    }

    /** A run with its totals, its author and whoever approved or paid it, in one read. */
    private static final String RUN_COLUMNS = """
            SELECT r.id,
                   r.period_year,
                   r.period_month,
                   r.status,
                   r.notes,
                   r.date_insert,
                   r.user_id,
                   author.user_name    AS author_name,
                   r.approved_at,
                   r.approved_by,
                   approver.user_name  AS approver_name,
                   r.paid_at,
                   r.paid_by,
                   payer.user_name     AS payer_name,
                   COALESCE(totals.line_count, 0)   AS line_count,
                   COALESCE(totals.total_earned, 0) AS total_earned,
                   COALESCE(totals.total_net, 0)    AS total_net
              FROM payroll_run r
              LEFT JOIN users author   ON author.id   = r.user_id
              LEFT JOIN users approver ON approver.id = r.approved_by
              LEFT JOIN users payer    ON payer.id    = r.paid_by
              LEFT JOIN (SELECT payroll_run_id,
                                COUNT(*)                                              AS line_count,
                                SUM(basic + allowances + commission)                  AS total_earned,
                                SUM(net_pay)                                          AS total_net
                           FROM payroll_line
                          GROUP BY payroll_run_id) totals ON totals.payroll_run_id = r.id
            """;

    public static String selectRunsSql() {
        return RUN_COLUMNS + " ORDER BY r.period_year DESC, r.period_month DESC LIMIT ?";
    }

    public static String selectRunByIdSql() {
        return RUN_COLUMNS + " WHERE r.id = ?";
    }

    public static String selectRunByPeriodSql() {
        return RUN_COLUMNS + " WHERE r.period_year = ? AND r.period_month = ?";
    }

    public static final String INSERT_RUN_SQL = """
            INSERT INTO payroll_run (period_year, period_month, status, notes, user_id)
            VALUES (?, ?, 'DRAFT', ?, ?)
            """;

    /**
     * Moves a run forward, and <b>only from the status the caller read</b>.
     * <p>
     * The {@code AND status = ?} is the whole race: two people pressing approve on the same
     * run both pass the check in Java, and exactly one of them updates a row. The second gets
     * zero back and is refused - the same shape as {@code UPDATE … WHERE redeemed_at IS NULL}
     * in the support-recovery challenge, and for the same reason.
     */
    public static final String APPROVE_RUN_SQL = """
            UPDATE payroll_run
               SET status = 'APPROVED', approved_at = NOW(), approved_by = ?
             WHERE id = ? AND status = ?
            """;

    public static final String PAY_RUN_SQL = """
            UPDATE payroll_run
               SET status = 'PAID', paid_at = NOW(), paid_by = ?
             WHERE id = ? AND status = ?
            """;

    public static final String CANCEL_RUN_SQL = """
            UPDATE payroll_run SET status = 'CANCELLED' WHERE id = ? AND status = ?
            """;

    public static final String DELETE_RUN_SQL = """
            DELETE FROM payroll_run WHERE id = ? AND status = 'DRAFT'
            """;

    public static final String SELECT_LINES_SQL = """
            SELECT l.id,
                   l.payroll_run_id,
                   l.employee_id,
                   e.column_name AS employee_name,
                   j.job_name    AS job_name,
                   l.salary_kind,
                   l.rate,
                   l.worked_days,
                   l.absence_days,
                   l.worked_hours,
                   l.basic,
                   l.allowances,
                   l.commission,
                   l.deductions,
                   l.absence_deduction,
                   l.advances_outstanding,
                   l.net_pay,
                   l.notes
              FROM payroll_line l
              JOIN employees e ON e.id = l.employee_id
              LEFT JOIN jobs j ON j.id = e.job
             WHERE l.payroll_run_id = ?
             ORDER BY e.column_name
            """;

    public static final String INSERT_LINE_SQL = """
            INSERT INTO payroll_line (payroll_run_id, employee_id, salary_kind, rate, worked_days,
                                      absence_days, worked_hours, basic, allowances, commission,
                                      deductions, absence_deduction, advances_outstanding,
                                      net_pay, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public static final String DELETE_LINES_SQL = """
            DELETE FROM payroll_line WHERE payroll_run_id = ?
            """;

    /**
     * Who the run is built for: everyone employed on any day of the period.
     * <p>
     * Not "everyone active today" - somebody who left in the middle of the month is owed the
     * part they worked, and reading the flag alone would drop them from the month they are
     * owed for. The rate is the one effective in the period, from the dated compensation
     * rather than from {@code employees.salary}, which means the hire salary and says so in
     * its own COMMENT.
     */
    public static final String SELECT_CANDIDATES_SQL = """
            SELECT e.id,
                   e.column_name,
                   e.hire_date,
                   e.end_date,
                   COALESCE(c.salary_kind, 'MONTHLY') AS salary_kind,
                   COALESCE(c.rate, e.salary, 0)      AS rate,
                   COALESCE(a.total, 0)               AS allowances,
                   COALESCE(adv.total, 0)             AS advances_outstanding
              FROM employees e
              LEFT JOIN (SELECT c1.employee_id, c1.salary_kind, c1.rate
                           FROM employee_compensation c1
                           JOIN (SELECT employee_id, MAX(effective_from) AS effective_from
                                   FROM employee_compensation
                                  WHERE effective_from <= ?
                                  GROUP BY employee_id) latest
                             ON latest.employee_id = c1.employee_id
                            AND latest.effective_from = c1.effective_from) c
                ON c.employee_id = e.id
              LEFT JOIN (SELECT employee_id, SUM(amount) AS total
                           FROM employee_allowance
                          WHERE is_active = 1
                            AND effective_from <= ?
                            AND (effective_to IS NULL OR effective_to >= ?)
                          GROUP BY employee_id) a
                ON a.employee_id = e.id
              LEFT JOIN (SELECT ed.emp_id, SUM(ed.amount) AS total
                           FROM expenses_details ed
                           JOIN employee_cash_purpose p ON p.expense_id = ed.id
                          WHERE p.purpose = 'ADVANCE'
                            AND ed.date <= ?
                          GROUP BY ed.emp_id) adv
                ON adv.emp_id = e.id
             WHERE (e.hire_date IS NULL OR e.hire_date <= ?)
               AND (e.end_date IS NULL OR e.end_date >= ?)
             ORDER BY e.column_name
            """;

    /** What a run wrote into the ledgers, so reversing it can take exactly that back. */
    public static final String DELETE_RUN_LEDGER_SQL = """
            DELETE FROM employee_ledger WHERE payroll_run_id = ?
            """;

    public static final String INSERT_RUN_LEDGER_SQL = """
            INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes,
                                         payroll_run_id, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    /** Whether a run has already been paid out, counted rather than trusted to the status. */
    public static final String COUNT_RUN_PAYMENTS_SQL = """
            SELECT COUNT(*) FROM employee_cash_purpose WHERE payroll_run_id = ?
            """;
}
