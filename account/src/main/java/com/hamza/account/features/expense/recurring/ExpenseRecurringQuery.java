package com.hamza.account.features.expense.recurring;

/**
 * Every statement over {@code expense_recurring}, pinned by {@code ExpenseRecurringQueryTest}.
 * <p>
 * <b>{@link #RECORDED_PERIODS_SQL} is what "already recorded" means</b>, and it reads
 * {@code expenses_details.recurring_id} - the link {@code V66} adds - rather than guessing from the
 * heading and the amount. The answer is a date per row; which period that date belongs to is
 * {@link ExpenseFrequency}'s to say, in Java, because a template's quarter starts where the template
 * starts and no {@code QUARTER()} expression knows that.
 */
public final class ExpenseRecurringQuery {

    private static final String COLUMNS = """
            SELECT r.id,
                   r.heading_id,
                   h.expenses_name AS heading_name,
                   p.expenses_name AS parent_heading_name,
                   r.treasury_id,
                   t.t_name AS treasury_name,
                   r.amount,
                   r.payee,
                   r.notes,
                   r.frequency,
                   r.day_of_month,
                   r.start_date,
                   r.end_date,
                   r.is_active
            """;

    /**
     * {@code LEFT} on the treasury: a till that has gone must not drop a template out of the list it is
     * corrected from - the rule the expenses list follows for the same reason.
     */
    private static final String FROM = """
            FROM expense_recurring r
                     JOIN expenses h ON h.id = r.heading_id
                     LEFT JOIN expenses p ON p.id = h.parent_id
                     LEFT JOIN treasury t ON t.id = r.treasury_id
            """;

    private static final String ORDER = "ORDER BY r.is_active DESC, COALESCE(p.expenses_name, h.expenses_name),"
            + " h.expenses_name";

    private ExpenseRecurringQuery() {
    }

    /** Every template, stopped ones included - this is the screen a stopped one is restarted from. */
    public static final String ALL_SQL = COLUMNS + FROM + ORDER;

    /** The templates that can fall due. */
    public static final String ACTIVE_SQL = COLUMNS + FROM + "WHERE r.is_active = 1\n" + ORDER;

    public static final String BY_ID_SQL = COLUMNS + FROM + "WHERE r.id = ?";

    /** The dates of every expense recorded from a template, for the templates that are active. */
    public static final String RECORDED_PERIODS_SQL = """
            SELECT d.recurring_id, d.date
            FROM expenses_details d
                     JOIN expense_recurring r ON r.id = d.recurring_id
            WHERE d.recurring_id IS NOT NULL
              AND r.is_active = 1
              AND d.date >= ?""";

    /** How many expenses were recorded from one template - what a delete has to answer for. */
    public static final String RECORDED_COUNT_SQL =
            "SELECT COUNT(*) FROM expenses_details WHERE recurring_id = ?";

    public static final String INSERT_SQL = """
            INSERT INTO expense_recurring (heading_id, treasury_id, amount, payee, notes, frequency,
                                           day_of_month, start_date, end_date, is_active, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * {@code user_id} is not written by an update: it records who created the template, and who changed
     * it afterwards is {@code audit_log}'s answer, written by a trigger whether the application asks or
     * not - the same rule the party ledger follows for {@code user_id}.
     */
    public static final String UPDATE_SQL = """
            UPDATE expense_recurring
            SET heading_id = ?, treasury_id = ?, amount = ?, payee = ?, notes = ?, frequency = ?,
                day_of_month = ?, start_date = ?, end_date = ?, is_active = ?
            WHERE id = ?""";

    public static final String DELETE_SQL = "DELETE FROM expense_recurring WHERE id = ?";
}
