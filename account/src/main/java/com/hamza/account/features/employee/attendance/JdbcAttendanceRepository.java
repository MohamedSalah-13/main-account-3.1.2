package com.hamza.account.features.employee.attendance;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The attendance's reads and writes, over {@link AttendanceQuery}. */
public final class JdbcAttendanceRepository extends AbstractDao<AttendanceDay>
        implements AttendanceRepository {

    @Override
    public List<AttendanceDay> daysBetween(LocalDate from, LocalDate to) throws DaoException {
        return readAll(AttendanceQuery.SELECT_DAYS_SQL, this::mapDay,
                Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public List<AttendanceDay> daysOf(int employeeId, LocalDate from, LocalDate to)
            throws DaoException {
        return readAll(AttendanceQuery.SELECT_EMPLOYEE_DAYS_SQL, this::mapDay,
                employeeId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public int upsertDay(int employeeId, LocalDate date, String status, BigDecimal hours,
                         Integer leaveTypeId, String notes, int userId) throws DaoException {
        return executeUpdate(AttendanceQuery.UPSERT_DAY_SQL,
                employeeId, Date.valueOf(date), status, hours, leaveTypeId, notes, userId);
    }

    @Override
    public int deleteDay(int employeeId, LocalDate date) throws DaoException {
        return executeUpdate(AttendanceQuery.DELETE_DAY_SQL, employeeId, Date.valueOf(date));
    }

    @Override
    public List<LeaveType> leaveTypes() throws DaoException {
        return readAll(AttendanceQuery.SELECT_LEAVE_TYPES_SQL, JdbcAttendanceRepository::mapType);
    }

    @Override
    public int insertLeaveType(String name, boolean paid, int annualLimit, boolean active,
                               String notes, int userId) throws DaoException {
        return insertReturningId(AttendanceQuery.INSERT_LEAVE_TYPE_SQL,
                name, paid ? 1 : 0, annualLimit, active ? 1 : 0, notes, userId);
    }

    @Override
    public int updateLeaveType(int id, String name, boolean paid, int annualLimit, boolean active,
                               String notes) throws DaoException {
        return executeUpdate(AttendanceQuery.UPDATE_LEAVE_TYPE_SQL,
                name, paid ? 1 : 0, annualLimit, active ? 1 : 0, notes, id);
    }

    @Override
    public List<LeaveRequest> requests(LeaveStatus status, int limit) throws DaoException {
        String stored = status == null ? null : status.name();
        return readAll(AttendanceQuery.SELECT_REQUESTS_SQL, JdbcAttendanceRepository::mapRequest,
                stored, stored, limit);
    }

    @Override
    public Optional<LeaveRequest> findRequest(int requestId) throws DaoException {
        for (LeaveRequest request : requests(null, 5000)) {
            if (request.id() == requestId) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    @Override
    public int insertRequest(int employeeId, int leaveTypeId, LocalDate from, LocalDate to,
                             String reason, int userId) throws DaoException {
        return insertReturningId(AttendanceQuery.INSERT_REQUEST_SQL,
                employeeId, leaveTypeId, Date.valueOf(from), Date.valueOf(to), reason, userId);
    }

    @Override
    public int decideRequest(int requestId, LeaveStatus decision, String note, int userId)
            throws DaoException {
        return executeUpdate(AttendanceQuery.DECIDE_REQUEST_SQL,
                decision.name(), note, userId, requestId);
    }

    @Override
    public int approvedDaysInYear(int employeeId, int leaveTypeId, int year,
                                  int excludingRequestId) throws DaoException {
        List<Integer> counts = readAll(AttendanceQuery.COUNT_APPROVED_DAYS_SQL,
                rs -> rs.getInt(1), employeeId, leaveTypeId, year, excludingRequestId);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    @Override
    public LocalDate[] employmentDates(int employeeId) throws DaoException {
        List<LocalDate[]> rows = readAll(AttendanceQuery.SELECT_EMPLOYMENT_DATES_SQL,
                rs -> new LocalDate[]{localDate(rs.getDate("hire_date")),
                        localDate(rs.getDate("end_date"))}, employeeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---- mapping -----------------------------------------------------------------------------

    private AttendanceDay mapDay(ResultSet rs) throws SQLException {
        return new AttendanceDay(
                rs.getInt("id"),
                rs.getInt("employee_id"),
                rs.getString("employee_name"),
                localDate(rs.getDate("work_date")),
                AttendanceStatus.of(rs.getString("status")),
                rs.getBigDecimal("hours"),
                nullableInt(rs, "leave_type_id"),
                rs.getString("leave_type_name"),
                rs.getInt("leave_is_paid") == 1,
                rs.getString("notes"));
    }

    private static LeaveType mapType(ResultSet rs) throws SQLException {
        return new LeaveType(rs.getInt("id"), rs.getString("type_name"),
                rs.getInt("is_paid") == 1, rs.getInt("annual_limit"),
                rs.getInt("is_active") == 1, rs.getString("notes"));
    }

    private static LeaveRequest mapRequest(ResultSet rs) throws SQLException {
        return new LeaveRequest(
                rs.getInt("id"),
                rs.getInt("employee_id"),
                rs.getString("employee_name"),
                rs.getInt("leave_type_id"),
                rs.getString("leave_type_name"),
                rs.getInt("leave_is_paid") == 1,
                localDate(rs.getDate("date_from")),
                localDate(rs.getDate("date_to")),
                LeaveStatus.of(rs.getString("status")),
                rs.getString("reason"),
                rs.getString("decision_note"),
                dateTime(rs.getTimestamp("decided_at")),
                nullableInt(rs, "decided_by"),
                rs.getString("decided_by_name"));
    }

    /**
     * Reads rows of a type other than this DAO's own - the answer the statement repository
     * arrived at, since {@code queryForObjects} is typed by the DAO's single parameter and
     * this one reads four shapes.
     */
    private <R> List<R> readAll(String sql, RowReader<R> reader, Object... parameters)
            throws DaoException {
        return withConnection(connection -> {
            List<R> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(reader.read(rs));
                    }
                }
            }
            return rows;
        });
    }

    @FunctionalInterface
    private interface RowReader<R> {
        R read(ResultSet rs) throws SQLException;
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime dateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public AttendanceDay map(ResultSet rs) throws DaoException {
        try {
            return mapDay(rs);
        } catch (SQLException e) {
            throw new DaoException("Could not read an attendance day", e);
        }
    }
}
