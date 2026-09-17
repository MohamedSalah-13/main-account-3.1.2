package com.hamza.account.features.expense;

import java.util.ArrayList;
import java.util.List;

/**
 * Every statement over {@code expenses_details}, in one place and pinned character for character by
 * {@code ExpenseQueryTest}.
 * <p>
 * Three properties hold here and each is a decision:
 * <ul>
 *   <li><b>The page, its summary and the print extract are built from one {@code WHERE}</b>
 *       ({@link #whereSql}). Filtered separately, the figures above the table describe a different set
 *       of expenses from the table - the rule {@code ItemsDao.catalogQuery} carries.</li>
 *   <li><b>No user value is ever concatenated.</b> The filter's shape decides the text; every value is
 *       a bound parameter, and the search patterns have their wildcards escaped.
 *       {@link JdbcExpenseRepository} binds in the order this class writes, and the test pins the
 *       count.</li>
 *   <li><b>It reads the table, not {@code expenses_details_view}.</b> That view inner-joins the heading
 *       and carries none of V64's columns; the joins here are written once, below, and every name a
 *       row shows comes off them rather than a query per row.</li>
 * </ul>
 */
public final class ExpenseQuery {

    private static final String COLUMNS = """
            SELECT d.id,
                   d.date,
                   d.type_code,
                   h.expenses_name AS heading_name,
                   p.expenses_name AS parent_heading_name,
                   h.employee_payment,
                   d.treasury_id,
                   t.t_name AS treasury_name,
                   d.amount,
                   d.payee,
                   d.reference_no,
                   d.notes,
                   d.emp_id,
                   e.column_name AS employee_name,
                   d.user_id,
                   u.user_name,
                   d.shift_id,
                   d.date_insert
            """;

    /**
     * {@code LEFT} everywhere but the heading. A treasury, an employee or a user row that has gone must
     * not drop the expense out of the list and out of its total - the way a deleted area once dropped a
     * customer. The heading is inner-joined because {@code type_code} is a non-null foreign key, so the
     * join can never lose a row.
     */
    private static final String FROM = """
            FROM expenses_details d
                     JOIN expenses h ON h.id = d.type_code
                     LEFT JOIN expenses p ON p.id = h.parent_id
                     LEFT JOIN treasury t ON t.id = d.treasury_id
                     LEFT JOIN employees e ON e.id = d.emp_id
                     LEFT JOIN users u ON u.id = d.user_id
            """;

    private ExpenseQuery() {
    }

    /** One page. Parameters: the filter's, then limit and offset. Newest first, as the old list was. */
    public static String pageSql(ExpenseFilter filter) {
        return COLUMNS + FROM + whereSql(filter) + "ORDER BY d.date DESC, d.id DESC\nLIMIT ? OFFSET ?";
    }

    /** One expense by its code, with its names. */
    public static final String BY_ID_SQL = COLUMNS + FROM + "WHERE d.id = ?";

    /** The count and the total over the whole filtered set. */
    public static String summarySql(ExpenseFilter filter) {
        return "SELECT COUNT(*) AS expense_count, COALESCE(SUM(d.amount), 0) AS total\n"
                + FROM + whereSql(filter);
    }

    /**
     * The heading the most was spent under, in the same set. A sub-heading counts under itself here -
     * this is "where did the money go", and rolling it into its main heading answers a coarser question
     * the report by heading asks instead.
     */
    public static String topHeadingSql(ExpenseFilter filter) {
        return "SELECT h.expenses_name AS heading_name, p.expenses_name AS parent_heading_name,"
                + " SUM(d.amount) AS total\n"
                + FROM + whereSql(filter)
                + "GROUP BY d.type_code, h.expenses_name, p.expenses_name\n"
                + "ORDER BY total DESC, h.expenses_name\n"
                + "LIMIT 1";
    }

    /** The people who have entered an expense, for the "entered by" filter. */
    public static final String USERS_SQL = """
            SELECT DISTINCT u.id, u.user_name
            FROM expenses_details d
                     JOIN users u ON u.id = d.user_id
            ORDER BY u.user_name""";

    /**
     * Payees already written, starting with what is being typed - the suggestions under the payee box.
     * Through V64's {@code payee} index.
     */
    public static final String PAYEES_SQL = """
            SELECT DISTINCT d.payee
            FROM expenses_details d
            WHERE d.payee LIKE ? ESCAPE '!'
            ORDER BY d.payee
            LIMIT ?""";

    /**
     * The insert. {@code emp_id} is written by one caller only - the employee payment - and is
     * {@code NULL} for every other; {@code shift_id} is the shift the gate answered with.
     */
    public static final String INSERT_SQL = """
            INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id,
                                          shift_id, payee, reference_no)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * The update, and what it leaves alone is the point.
     * <p>
     * Not {@code emp_id}: the form that edits an expense has no employee on it, so writing the column
     * from the form would take the employee off every salary somebody corrected - the trap
     * {@code UsersService.update} fell into with {@code user_activity}. Not {@code user_id}, which
     * records who entered the expense. Not {@code shift_id}, which the cash left under and an edit does
     * not move - the shift journal records the correction instead.
     */
    public static final String UPDATE_SQL = """
            UPDATE expenses_details
            SET type_code = ?, date = ?, amount = ?, notes = ?, treasury_id = ?, payee = ?, reference_no = ?
            WHERE id = ?""";

    public static final String DELETE_SQL = "DELETE FROM expenses_details WHERE id = ?";

    /**
     * Every condition the filter sets, in the order {@link JdbcExpenseRepository} binds them.
     * <p>
     * The text match is bracketed as a whole: {@code a OR b AND c} is {@code a OR (b AND c)}, and an
     * unbracketed alternation would leave one branch of the search unfiltered by the period - which
     * reads on screen as a period that works sometimes.
     */
    public static String whereSql(ExpenseFilter filter) {
        List<String> conditions = new ArrayList<>();
        if (filter.from() != null) {
            conditions.add("d.date >= ?");
        }
        if (filter.to() != null) {
            conditions.add("d.date <= ?");
        }
        if (filter.headingId() != null) {
            conditions.add("(d.type_code = ? OR h.parent_id = ?)");
        }
        if (filter.treasuryId() != null) {
            conditions.add("d.treasury_id = ?");
        }
        if (filter.userId() != null) {
            conditions.add("d.user_id = ?");
        }
        if (filter.minAmount() != null) {
            conditions.add("d.amount >= ?");
        }
        if (filter.maxAmount() != null) {
            conditions.add("d.amount <= ?");
        }
        if (filter.hasText()) {
            conditions.add("(d.id = ? OR h.expenses_name LIKE ? ESCAPE '!' OR p.expenses_name LIKE ? ESCAPE '!'"
                    + " OR d.payee LIKE ? ESCAPE '!' OR d.reference_no LIKE ? ESCAPE '!'"
                    + " OR d.notes LIKE ? ESCAPE '!' OR e.column_name LIKE ? ESCAPE '!')");
        }
        return conditions.isEmpty() ? "" : "WHERE " + String.join("\n  AND ", conditions) + "\n";
    }

    /** How many parameters {@link #whereSql} binds for this filter - what the test holds the binder to. */
    public static int whereParameterCount(ExpenseFilter filter) {
        int count = 0;
        if (filter.from() != null) count++;
        if (filter.to() != null) count++;
        if (filter.headingId() != null) count += 2;
        if (filter.treasuryId() != null) count++;
        if (filter.userId() != null) count++;
        if (filter.minAmount() != null) count++;
        if (filter.maxAmount() != null) count++;
        if (filter.hasText()) count += 7;
        return count;
    }

    /** A contains-match with the wildcards a person may legitimately type escaped out of it. */
    public static String containsPattern(String text) {
        return "%" + escape(text) + "%";
    }

    /** A starts-with match. */
    public static String startsPattern(String text) {
        return escape(text) + "%";
    }

    private static String escape(String text) {
        String value = text == null ? "" : text.strip();
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
