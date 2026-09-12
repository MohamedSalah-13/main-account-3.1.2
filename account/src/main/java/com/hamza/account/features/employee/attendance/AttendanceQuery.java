package com.hamza.account.features.employee.attendance;

/** Every statement the attendance runs over, pinned by {@code AttendanceQueryTest}. */
public final class AttendanceQuery {

    private AttendanceQuery() {
    }

    /**
     * A month of days for everyone, for the grid.
     * <p>
     * The leave type is joined rather than copied onto the row, so a later correction to
     * {@code is_paid} corrects every day already recorded instead of leaving two answers.
     */
    public static final String SELECT_DAYS_SQL = """
            SELECT a.id,
                   a.employee_id,
                   e.column_name AS employee_name,
                   a.work_date,
                   a.status,
                   a.hours,
                   a.leave_type_id,
                   t.type_name    AS leave_type_name,
                   COALESCE(t.is_paid, 0) AS leave_is_paid,
                   a.notes
              FROM attendance a
              JOIN employees e ON e.id = a.employee_id
              LEFT JOIN leave_type t ON t.id = a.leave_type_id
             WHERE a.work_date BETWEEN ? AND ?
             ORDER BY e.column_name, a.work_date
            """;

    public static final String SELECT_EMPLOYEE_DAYS_SQL = """
            SELECT a.id,
                   a.employee_id,
                   e.column_name AS employee_name,
                   a.work_date,
                   a.status,
                   a.hours,
                   a.leave_type_id,
                   t.type_name    AS leave_type_name,
                   COALESCE(t.is_paid, 0) AS leave_is_paid,
                   a.notes
              FROM attendance a
              JOIN employees e ON e.id = a.employee_id
              LEFT JOIN leave_type t ON t.id = a.leave_type_id
             WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
             ORDER BY a.work_date
            """;

    /**
     * Records a day, or corrects the one already there.
     * <p>
     * {@code ON DUPLICATE KEY UPDATE} rather than a delete and an insert: the unique key is
     * what guarantees one row per employee per day, and correcting a day has to be the same
     * operation as recording it or the grid would leave a gap between the two.
     */
    public static final String UPSERT_DAY_SQL = """
            INSERT INTO attendance (employee_id, work_date, status, hours, leave_type_id, notes,
                                    user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE status        = VALUES(status),
                                    hours         = VALUES(hours),
                                    leave_type_id = VALUES(leave_type_id),
                                    notes         = VALUES(notes),
                                    user_id       = VALUES(user_id)
            """;

    public static final String DELETE_DAY_SQL = """
            DELETE FROM attendance WHERE employee_id = ? AND work_date = ?
            """;

    public static final String SELECT_LEAVE_TYPES_SQL = """
            SELECT id, type_name, is_paid, annual_limit, is_active, notes
              FROM leave_type
             ORDER BY type_name
            """;

    public static final String INSERT_LEAVE_TYPE_SQL = """
            INSERT INTO leave_type (type_name, is_paid, annual_limit, is_active, notes, user_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    public static final String UPDATE_LEAVE_TYPE_SQL = """
            UPDATE leave_type
               SET type_name = ?, is_paid = ?, annual_limit = ?, is_active = ?, notes = ?
             WHERE id = ?
            """;

    public static final String SELECT_REQUESTS_SQL = """
            SELECT r.id,
                   r.employee_id,
                   e.column_name AS employee_name,
                   r.leave_type_id,
                   t.type_name   AS leave_type_name,
                   t.is_paid     AS leave_is_paid,
                   r.date_from,
                   r.date_to,
                   r.status,
                   r.reason,
                   r.decision_note,
                   r.decided_at,
                   r.decided_by,
                   d.user_name   AS decided_by_name
              FROM leave_request r
              JOIN employees e ON e.id = r.employee_id
              JOIN leave_type t ON t.id = r.leave_type_id
              LEFT JOIN users d ON d.id = r.decided_by
             WHERE (? IS NULL OR r.status = ?)
             ORDER BY r.date_from DESC, r.id DESC
             LIMIT ?
            """;

    public static final String INSERT_REQUEST_SQL = """
            INSERT INTO leave_request (employee_id, leave_type_id, date_from, date_to, reason,
                                       user_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    /**
     * Decides a request, and <b>only one that is still pending</b>.
     * <p>
     * The {@code AND status = 'PENDING'} is the race: two people deciding the same request
     * both pass the check in Java and exactly one updates a row - the shape V59's approval
     * uses, and the support-recovery challenge before it.
     */
    public static final String DECIDE_REQUEST_SQL = """
            UPDATE leave_request
               SET status = ?, decision_note = ?, decided_at = NOW(), decided_by = ?
             WHERE id = ? AND status = 'PENDING'
            """;

    /**
     * How many days of a type an employee has already had approved in a year.
     * <p>
     * Counted from the requests rather than from the attendance grid: the limit is about what
     * was granted, and a day nobody got round to marking on the grid was still granted.
     */
    public static final String COUNT_APPROVED_DAYS_SQL = """
            SELECT COALESCE(SUM(DATEDIFF(r.date_to, r.date_from) + 1), 0)
              FROM leave_request r
             WHERE r.employee_id = ?
               AND r.leave_type_id = ?
               AND r.status = 'APPROVED'
               AND YEAR(r.date_from) = ?
               AND r.id <> ?
            """;

    /** The hire and end dates, so a day outside employment is refused by the service. */
    public static final String SELECT_EMPLOYMENT_DATES_SQL = """
            SELECT hire_date, end_date FROM employees WHERE id = ?
            """;
}
