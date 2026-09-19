package com.hamza.account.features.delegate;

/**
 * Every statement over {@code employee_commission_rule}, pinned character for character by
 * {@code CommissionRuleQueryTest} together with each one's parameter count - a merge that swaps
 * two adjacent columns still produces valid SQL, it just stores a threshold as a rate.
 */
public final class CommissionRuleQuery {

    private static final String COLUMNS = """
            id, employee_id, effective_from, basis, tier_mode, target,
                   tier1_from, tier1_rate, tier2_from, tier2_rate, tier3_from, tier3_rate, notes""";

    /** Newest first: the rule at the top is the one the next month will be judged by. */
    public static final String HISTORY_SQL = """
            SELECT %s
            FROM employee_commission_rule
            WHERE employee_id = ?
            ORDER BY effective_from DESC""".formatted(COLUMNS);

    /**
     * The rule in force on a day: the latest one that had started by then. A rule dated in the
     * future is in the table and is not this answer until its day comes.
     */
    public static final String IN_FORCE_SQL = """
            SELECT %s
            FROM employee_commission_rule
            WHERE employee_id = ?
              AND effective_from <= ?
            ORDER BY effective_from DESC
            LIMIT 1""".formatted(COLUMNS);

    public static final String INSERT_SQL = """
            INSERT INTO employee_commission_rule (employee_id, effective_from, basis, tier_mode, target,
                   tier1_from, tier1_rate, tier2_from, tier2_rate, tier3_from, tier3_rate, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * {@code user_id} is who <b>entered</b> the rule and is not rewritten here; who changed it
     * afterwards is the audit log's answer.
     */
    public static final String UPDATE_SQL = """
            UPDATE employee_commission_rule
            SET basis = ?, tier_mode = ?, target = ?,
                tier1_from = ?, tier1_rate = ?, tier2_from = ?, tier2_rate = ?,
                tier3_from = ?, tier3_rate = ?, notes = ?
            WHERE employee_id = ?
              AND effective_from = ?""";

    /** Both keys, so an id from one delegate's screen cannot remove another delegate's rule. */
    public static final String DELETE_SQL = """
            DELETE FROM employee_commission_rule
            WHERE id = ?
              AND employee_id = ?""";

    /** A commission rule belongs to somebody invoices can name, which is what the flag says. */
    public static final String IS_DELEGATE_SQL = """
            SELECT COUNT(*)
            FROM employees e
                     JOIN jobs j ON j.id = e.job
            WHERE e.id = ?
              AND j.is_delegate = 1""";

    private CommissionRuleQuery() {
    }
}
