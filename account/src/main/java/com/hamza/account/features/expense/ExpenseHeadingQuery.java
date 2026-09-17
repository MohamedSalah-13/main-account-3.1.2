package com.hamza.account.features.expense;

/**
 * Every statement over {@code expenses} - the headings - pinned character for character by
 * {@code ExpenseHeadingQueryTest}.
 * <p>
 * The parent is joined {@code LEFT}: a main heading has none, and an inner join would drop every main
 * heading out of every list, which is the mistake the customer's area join made for a year.
 * <p>
 * The order is the tree's: a main heading, then the sub-headings under it, then the next main
 * heading. {@code COALESCE(p.expenses_name, h.expenses_name)} files a sub-heading beside its parent,
 * and {@code h.parent_id IS NOT NULL} puts the parent first within that group.
 */
public final class ExpenseHeadingQuery {

    private static final String COLUMNS = """
            SELECT h.id,
                   h.expenses_name,
                   h.parent_id,
                   p.expenses_name AS parent_name,
                   h.is_active,
                   h.system_key,
                   h.employee_payment
            FROM expenses h
                     LEFT JOIN expenses p ON p.id = h.parent_id
            """;

    private static final String ORDER = """
            ORDER BY COALESCE(p.expenses_name, h.expenses_name),
                     h.parent_id IS NOT NULL,
                     h.expenses_name""";

    /** Every heading, stopped ones included. */
    public static final String ALL_SQL = COLUMNS + ORDER;

    /** One heading by its code. */
    public static final String BY_ID_SQL = COLUMNS + "WHERE h.id = ?";

    /** The heading the system depends on under this key. */
    public static final String BY_SYSTEM_KEY_SQL = COLUMNS + "WHERE h.system_key = ?";

    /**
     * How many expenses each heading holds and what they came to since a day - the two figures the
     * headings screen shows beside each row. Grouped before it is joined to anything, through the
     * {@code (type_code, date)} index V64 adds.
     */
    public static final String USAGE_SQL = """
            SELECT d.type_code,
                   COUNT(*) AS expense_count,
                   COALESCE(SUM(CASE WHEN d.date >= ? THEN d.amount ELSE 0 END), 0) AS total_since
            FROM expenses_details d
            GROUP BY d.type_code""";

    public static final String NAME_TAKEN_SQL =
            "SELECT COUNT(*) FROM expenses WHERE expenses_name = ? AND id <> ?";

    public static final String INSERT_SQL = """
            INSERT INTO expenses (expenses_name, parent_id, is_active, employee_payment, user_id)
            VALUES (?, ?, ?, ?, ?)""";

    /**
     * The update names only what the screen owns. Not {@code system_key} - see
     * {@link ExpenseHeadingDraft} - and not {@code user_id}, which records who created the heading and
     * is not rewritten by whoever renames it later.
     */
    public static final String UPDATE_SQL = """
            UPDATE expenses
            SET expenses_name = ?, parent_id = ?, is_active = ?, employee_payment = ?
            WHERE id = ?""";

    public static final String DELETE_SQL = "DELETE FROM expenses WHERE id = ?";

    private ExpenseHeadingQuery() {
    }
}
