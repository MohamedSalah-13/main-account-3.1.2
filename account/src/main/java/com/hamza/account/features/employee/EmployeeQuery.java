package com.hamza.account.features.employee;

import java.util.ArrayList;
import java.util.List;

/**
 * Every statement over {@code employees} and {@code employee_compensation}, in one place and
 * pinned character for character by {@code EmployeeQueryTest}.
 * <p>
 * Three properties hold here and each is a decision:
 * <ul>
 *   <li><b>The page and its summary are built from one {@code WHERE}</b> ({@link #whereSql}).
 *       Filtering them separately is what makes a pagination control describe a different set
 *       of rows from the table it sits under - the rule {@code ItemsDao.catalogQuery} carries.</li>
 *   <li><b>No user value is ever concatenated.</b> Only the filter's shape decides the text of
 *       the statement; every value it compares against is a bound parameter, and the search
 *       patterns have their wildcards escaped. {@link JdbcEmployeeRepository} binds them in
 *       the order this class writes them, and the test pins the count.</li>
 *   <li><b>A salary column is projected only when the reader may see it.</b> Not hidden
 *       afterwards: the figure does not cross the connection at all. The employees screen used
 *       to read every salary in the shop and then set {@code setVisible(false)} on the column -
 *       which is a UI hint, and the {@code AuthorizationGuard} javadoc says in as many words
 *       that hiding a control is not enforcement.</li>
 * </ul>
 */
public final class EmployeeQuery {

    /**
     * The list projection.
     * <p>
     * {@code image} is deliberately absent - it is a {@code LONGBLOB}, and selecting it to draw
     * a name is the {@code ItemsDao.map} mistake at a different table. {@link #PHOTO_SQL} reads
     * one picture when a profile asks for it.
     */
    private static final String COLUMNS = """
            SELECT e.id,
                   e.column_name,
                   e.job,
                   j.job_name,
                   j.is_delegate,
                   e.birth_date,
                   e.hire_date,
                   e.end_date,
                   e.is_active,
                   e.employment_type,
                   e.national_id,
                   e.email,
                   e.tel,
                   e.address,
                   e.notes,
                   e.default_treasury_id,
                   e.date_insert,
                   c.salary_kind,
            """;

    /** What a reader holding {@code employees.show.salary} additionally gets. */
    private static final String SALARY_COLUMNS = """
                   e.salary AS hire_salary,
                   c.rate AS current_rate,
                   c.effective_from AS rate_from
            """;

    /** The same three columns, empty. The row shape stays identical so one mapper reads both. */
    private static final String NO_SALARY_COLUMNS = """
                   NULL AS hire_salary,
                   NULL AS current_rate,
                   NULL AS rate_from
            """;

    /**
     * {@code employee_current_compensation} is joined LEFT on purpose: an employee whose only
     * compensation row is dated in the future - a raise entered the day it was agreed - is not
     * in that view, and an inner join would drop them out of the list entirely.
     */
    private static final String FROM = """
            FROM employees e
                     JOIN jobs j ON j.id = e.job
                     LEFT JOIN employee_current_compensation c ON c.employee_id = e.id
            """;

    private EmployeeQuery() {
    }

    /** One page of the filtered list. Parameters: the filter's, then the order's, then limit and offset. */
    public static String pageSql(EmployeeFilter filter, boolean salaryVisible) {
        return COLUMNS + (salaryVisible ? SALARY_COLUMNS : NO_SALARY_COLUMNS) + FROM
                + whereSql(filter) + orderSql(filter) + "LIMIT ? OFFSET ?";
    }

    /** One employee in full, by code. */
    public static String byIdSql(boolean salaryVisible) {
        return COLUMNS + (salaryVisible ? SALARY_COLUMNS : NO_SALARY_COLUMNS) + FROM
                + "WHERE e.id = ?";
    }

    /**
     * The four figures above the table, over the whole filtered set rather than the page.
     * <p>
     * They are filters as much as facts - "delegates: 4" is a number somebody otherwise has to
     * go and find - so they are counted through the same {@code WHERE} the rows are.
     * <p>
     * The payroll total counts <b>monthly rates of employees still working</b>, and nothing
     * else: adding a daily wage to a monthly salary produces a number that is not anybody's
     * wage bill. A reader without the salary permission gets {@code NULL}, not zero - zero is
     * an answer, and this is the absence of one.
     */
    public static String summarySql(EmployeeFilter filter, boolean salaryVisible) {
        String payroll = salaryVisible
                ? "SUM(CASE WHEN e.is_active = 1 AND c.salary_kind = 'MONTHLY' THEN c.rate ELSE 0 END)"
                : "NULL";
        return "SELECT COUNT(*) AS employees,\n"
                + "       SUM(e.is_active = 1) AS active_employees,\n"
                + "       SUM(j.is_delegate = 1) AS delegates,\n"
                + "       " + payroll + " AS monthly_payroll\n"
                + FROM + whereSql(filter);
    }

    /** The picture, on its own, for the one screen that shows it. */
    public static final String PHOTO_SQL = "SELECT image FROM employees WHERE id = ?";

    /**
     * Names alone, for a combo box.
     * <p>
     * It reads names rather than rows because {@code salary} is a column on the table: filling
     * a dropdown used to pull the whole payroll across the connection, and nothing displayed
     * it - the leak was in what was fetched.
     */
    public static String namesSql(EmployeeScope scope, boolean delegatesOnly) {
        return "SELECT e.column_name FROM employees e JOIN jobs j ON j.id = e.job"
                + scopeSql(scope, delegatesOnly) + " ORDER BY e.column_name";
    }

    /** The same rows with their codes, for a combo that has to hand an id back. */
    public static String lookupSql(EmployeeScope scope, boolean delegatesOnly) {
        return "SELECT e.id, e.column_name FROM employees e JOIN jobs j ON j.id = e.job"
                + scopeSql(scope, delegatesOnly) + " ORDER BY e.column_name";
    }

    /** One employee's code and name by name, which is all a saved document needs to resolve one. */
    public static final String LOOKUP_BY_NAME_SQL =
            "SELECT e.id, e.column_name FROM employees e WHERE e.column_name = ?";

    /** One employee's code and name by code. */
    public static final String LOOKUP_BY_ID_SQL =
            "SELECT e.id, e.column_name FROM employees e WHERE e.id = ?";

    private static String scopeSql(EmployeeScope scope, boolean delegatesOnly) {
        List<String> conditions = new ArrayList<>();
        if (delegatesOnly) {
            conditions.add("j.is_delegate = 1");
        }
        if (scope == EmployeeScope.ACTIVE_ONLY) {
            conditions.add("e.is_active = 1");
            conditions.add("j.is_active = 1");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * The insert.
     * <p>
     * {@code salary} is written here and <b>nowhere else</b>: it is the salary at hire, and
     * {@link #UPDATE_SQL} does not name it. What somebody is paid now is a dated row, so the
     * only way to change it is to record one - which is what {@code SalaryChangeGuard}
     * enforces and what the old screen could not do at all.
     */
    public static final String INSERT_SQL = """
            INSERT INTO employees (column_name, job, birth_date, hire_date, end_date, salary,
                                   email, tel, address, national_id, employment_type,
                                   default_treasury_id, notes, is_active, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * The update, and what it leaves alone is the point.
     * <p>
     * It names only the columns the form owns. Not {@code salary} (a dated row), not
     * {@code is_active} (its own statement), not {@code image}, not {@code user_id} - which
     * records who entered the employee and is not rewritten by whoever edits the row later.
     * <p>
     * An update that writes the whole row has to carry forward every column the screen has no
     * control for, and the one that does not is found the hard way: {@code UsersService.update}
     * wrote {@code user_activity} from a model the edit screen never filled, and so quietly
     * reactivated every deactivated account it touched. Naming fewer columns removes the
     * question rather than answering it.
     */
    public static final String UPDATE_SQL = """
            UPDATE employees
            SET column_name = ?, job = ?, birth_date = ?, hire_date = ?, end_date = ?,
                email = ?, tel = ?, address = ?, national_id = ?, employment_type = ?,
                default_treasury_id = ?, notes = ?
            WHERE id = ?""";

    /**
     * Correcting the salary the employee was hired at.
     * <p>
     * Allowed only while that figure is still the only thing anyone has ever been told - see
     * {@link SalaryChangeGuard}. Both halves move together: the column and the single
     * compensation row that was copied from it, because leaving one behind is how a screen
     * ends up showing two answers to one question.
     */
    public static final String UPDATE_HIRE_SALARY_SQL = "UPDATE employees SET salary = ? WHERE id = ?";

    public static final String SET_ACTIVE_SQL = "UPDATE employees SET is_active = ? WHERE id = ?";

    public static final String UPDATE_IMAGE_SQL = "UPDATE employees SET image = ? WHERE id = ?";

    public static final String NAME_TAKEN_SQL =
            "SELECT COUNT(*) FROM employees WHERE column_name = ? AND id <> ?";

    // ---- compensation -------------------------------------------------------------------

    public static final String INSERT_COMPENSATION_SQL = """
            INSERT INTO employee_compensation (employee_id, effective_from, salary_kind, rate,
                                               notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?)""";

    /**
     * A rate already recorded for that day is replaced rather than refused: the unique key is
     * (employee, day), and a correction made the same afternoon is an edit of that decision,
     * not a second one.
     */
    public static final String UPDATE_COMPENSATION_SQL = """
            UPDATE employee_compensation
            SET salary_kind = ?, rate = ?, notes = ?
            WHERE employee_id = ? AND effective_from = ?""";

    public static final String COMPENSATION_HISTORY_SQL = """
            SELECT id, employee_id, effective_from, salary_kind, rate, notes
            FROM employee_compensation
            WHERE employee_id = ?
            ORDER BY effective_from DESC""";

    public static final String COMPENSATION_COUNT_SQL =
            "SELECT COUNT(*) FROM employee_compensation WHERE employee_id = ?";

    public static final String DELETE_COMPENSATION_SQL =
            "DELETE FROM employee_compensation WHERE id = ? AND employee_id = ?";

    // ---- jobs ---------------------------------------------------------------------------

    public static String jobsSql(boolean activeOnly) {
        return """
                SELECT j.id, j.job_name, j.is_delegate, j.is_active, j.default_salary, j.notes
                FROM jobs j
                """
                + (activeOnly ? "WHERE j.is_active = 1\n" : "")
                + "ORDER BY j.job_name";
    }

    public static final String INSERT_JOB_SQL = """
            INSERT INTO jobs (job_name, is_delegate, is_active, default_salary, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?)""";

    public static final String UPDATE_JOB_SQL = """
            UPDATE jobs
            SET job_name = ?, is_delegate = ?, is_active = ?, default_salary = ?, notes = ?
            WHERE id = ?""";

    public static final String JOB_NAME_TAKEN_SQL =
            "SELECT COUNT(*) FROM jobs WHERE job_name = ? AND id <> ?";

    /** How many employees hold this job - what the screen says before it refuses a delete. */
    public static final String JOB_USAGE_SQL = "SELECT COUNT(*) FROM employees WHERE job = ?";

    // ---- the shared WHERE ---------------------------------------------------------------

    /**
     * Every condition the filter sets, in the order {@link JdbcEmployeeRepository} binds them.
     * <p>
     * The text match is bracketed as a whole. {@code a OR b AND c} is {@code a OR (b AND c)} in
     * SQL, so an unbracketed alternation would leave one branch of the search unfiltered by
     * everything else on the bar - which reads on screen as a filter that works sometimes.
     */
    public static String whereSql(EmployeeFilter filter) {
        List<String> conditions = new ArrayList<>();
        if (filter.hasText()) {
            conditions.add("(e.column_name LIKE ? ESCAPE '!' OR e.tel LIKE ? ESCAPE '!'"
                    + " OR e.national_id LIKE ? ESCAPE '!' OR e.id = ?)");
        }
        if (filter.jobId() != null) {
            conditions.add("e.job = ?");
        }
        if (filter.state().active() != null) {
            conditions.add("e.is_active = ?");
        }
        if (filter.delegatesOnly()) {
            conditions.add("j.is_delegate = 1");
        }
        if (filter.salaryKind() != null) {
            conditions.add("c.salary_kind = ?");
        }
        if (filter.employmentType() != null) {
            conditions.add("e.employment_type = ?");
        }
        if (filter.hiredFrom() != null) {
            conditions.add("e.hire_date >= ?");
        }
        if (filter.hiredTo() != null) {
            conditions.add("e.hire_date <= ?");
        }
        if (filter.minRate() != null) {
            conditions.add("c.rate >= ?");
        }
        if (filter.maxRate() != null) {
            conditions.add("c.rate <= ?");
        }
        return conditions.isEmpty() ? "" : "WHERE " + String.join("\n  AND ", conditions) + "\n";
    }

    /**
     * Relevance first when there is something to be relevant to, then the people still working,
     * then the name.
     * <p>
     * This is the three-phase search the old DAO ran as three round trips - exact code or
     * telephone, then names starting with the text, then names containing it - merged into one
     * statement. It ran them as separate queries into a {@code LinkedHashMap} capped at fifty
     * rows, which is why a search had no second page: the order existed only in that map.
     */
    private static String orderSql(EmployeeFilter filter) {
        if (!filter.hasText()) {
            return "ORDER BY e.is_active DESC, e.column_name\n";
        }
        return """
                ORDER BY CASE WHEN e.id = ? OR e.tel = ? THEN 0
                              WHEN e.column_name LIKE ? ESCAPE '!' THEN 1
                              ELSE 2 END,
                         e.is_active DESC,
                         e.column_name
                """;
    }

    /** A contains-match with the wildcards a person may legitimately type escaped out of it. */
    public static String containsPattern(String text) {
        return "%" + escape(text) + "%";
    }

    /** A starts-with match, which is what the middle phase of the search ranks on. */
    public static String startsPattern(String text) {
        return escape(text) + "%";
    }

    private static String escape(String text) {
        String value = text == null ? "" : text.strip();
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
