package com.hamza.account.features.expense.budget;

/**
 * Every statement over {@code expense_budget}, pinned by {@code ExpenseBudgetQueryTest}.
 * <p>
 * {@code month} and {@code year} are quoted: both are MySQL function names, and an unquoted column of
 * that name is a thing people trip over when they edit this later. {@code month_key} is the generated
 * column the unique index is on - it is never written, because the database computes it.
 */
public final class ExpenseBudgetQuery {

    private static final String COLUMNS = """
            SELECT b.id,
                   b.heading_id,
                   h.expenses_name AS heading_name,
                   p.expenses_name AS parent_heading_name,
                   b.`year`,
                   b.`month`,
                   b.amount,
                   b.notes
            """;

    /** The heading is inner-joined: {@code heading_id} is a non-null key, so the join loses no row. */
    private static final String FROM = """
            FROM expense_budget b
                     JOIN expenses h ON h.id = b.heading_id
                     LEFT JOIN expenses p ON p.id = h.parent_id
            """;

    private static final String ORDER = "ORDER BY COALESCE(p.expenses_name, h.expenses_name), h.expenses_name,"
            + " b.`year`, b.month_key";

    private ExpenseBudgetQuery() {
    }

    /** Every budget of one year, its monthly rows and its yearly one together. */
    public static final String BY_YEAR_SQL = COLUMNS + FROM + "WHERE b.`year` = ?\n" + ORDER;

    /**
     * The budgets that touch a period at all - a yearly budget of a year the period runs through, and a
     * monthly budget of a month it covers. The report decides what each is worth; this only fetches.
     */
    public static final String FOR_PERIOD_SQL = COLUMNS + FROM + """
            WHERE (b.`month` IS NULL AND b.`year` BETWEEN ? AND ?)
               OR (b.`month` IS NOT NULL AND (b.`year` * 100 + b.`month`) BETWEEN ? AND ?)
            """ + ORDER;

    public static final String BY_ID_SQL = COLUMNS + FROM + "WHERE b.id = ?";

    /** Whether this heading already has a budget for this period, ignoring the row being edited. */
    public static final String TAKEN_SQL = """
            SELECT COUNT(*)
            FROM expense_budget
            WHERE heading_id = ?
              AND `year` = ?
              AND month_key = COALESCE(?, 0)
              AND id <> ?""";

    public static final String INSERT_SQL = """
            INSERT INTO expense_budget (heading_id, `year`, `month`, amount, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?)""";

    /**
     * The heading and the period are not written by an update: moving a budget from one heading to
     * another is a different budget, and the form has no control for it - the trap
     * {@code UsersService.update} fell into with {@code user_activity}.
     */
    public static final String UPDATE_SQL = """
            UPDATE expense_budget
            SET amount = ?, notes = ?
            WHERE id = ?""";

    public static final String DELETE_SQL = "DELETE FROM expense_budget WHERE id = ?";

    /** The years that have a budget at all, newest first - what the screen's year picker offers. */
    public static final String YEARS_SQL = "SELECT DISTINCT `year` FROM expense_budget ORDER BY `year` DESC";
}
