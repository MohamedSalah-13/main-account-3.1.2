package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.SalaryKind;
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

/** The payroll's reads and writes, over {@link PayrollQuery}. */
public final class JdbcPayrollRepository extends AbstractDao<PayrollRun>
        implements PayrollRepository {

    @Override
    public List<PayrollRun> recentRuns(int limit) throws DaoException {
        return queryForObjects(PayrollQuery.selectRunsSql(), this::mapRun, limit);
    }

    @Override
    public Optional<PayrollRun> findRun(int runId) throws DaoException {
        List<PayrollRun> rows = queryForObjects(PayrollQuery.selectRunByIdSql(), this::mapRun, runId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<PayrollRun> findRunForPeriod(PayrollPeriod period) throws DaoException {
        List<PayrollRun> rows = queryForObjects(PayrollQuery.selectRunByPeriodSql(), this::mapRun,
                period.year(), period.month());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public int insertRun(PayrollPeriod period, String notes, int userId) throws DaoException {
        return insertReturningId(PayrollQuery.INSERT_RUN_SQL,
                period.year(), period.month(), notes, userId);
    }

    @Override
    public int moveStatus(int runId, PayrollRunStatus expected, PayrollRunStatus next, int userId)
            throws DaoException {
        return switch (next) {
            case APPROVED -> executeUpdate(PayrollQuery.APPROVE_RUN_SQL, userId, runId, expected.name());
            case PAID -> executeUpdate(PayrollQuery.PAY_RUN_SQL, userId, runId, expected.name());
            case CANCELLED -> executeUpdate(PayrollQuery.CANCEL_RUN_SQL, runId, expected.name());
            case DRAFT -> 0;
        };
    }

    @Override
    public int deleteDraftRun(int runId) throws DaoException {
        return executeUpdate(PayrollQuery.DELETE_RUN_SQL, runId);
    }

    @Override
    public List<PayrollLine> linesOf(int runId) throws DaoException {
        return readAll(PayrollQuery.SELECT_LINES_SQL, this::mapLine, runId);
    }

    @Override
    public List<PayrollInput> candidatesFor(PayrollPeriod period) throws DaoException {
        Date first = Date.valueOf(period.firstDay());
        Date last = Date.valueOf(period.lastDay());
        return readAll(PayrollQuery.SELECT_CANDIDATES_SQL, this::mapCandidate,
                last, last, first, last, last, first);
    }

    @Override
    public int deleteLines(int runId) throws DaoException {
        return executeUpdate(PayrollQuery.DELETE_LINES_SQL, runId);
    }

    @Override
    public int updateLine(int runId, int lineId, PayrollCalculation line, PayrollInput input,
                          String notes) throws DaoException {
        return executeUpdate(PayrollQuery.UPDATE_LINE_SQL,
                input.workedDays(), input.absenceDays(), input.workedHours(), line.basic(),
                line.commission(), line.deductions(), line.absenceDeduction(), line.netPay(),
                notes, lineId, runId);
    }

    @Override
    public int insertLine(int runId, PayrollCalculation line, PayrollInput input, int userId)
            throws DaoException {
        return insertReturningId(PayrollQuery.INSERT_LINE_SQL,
                runId, line.employeeId(), line.salaryKind().name(), line.rate(),
                input.workedDays(), input.absenceDays(), input.workedHours(),
                line.basic(), line.allowances(), line.commission(), line.deductions(),
                line.absenceDeduction(), input.advancesOutstanding(), line.netPay(),
                null, userId);
    }

    @Override
    public int insertRunLedgerEntry(int employeeId, LocalDate date, String kind, BigDecimal amount,
                                    String notes, int runId, int userId) throws DaoException {
        return insertReturningId(PayrollQuery.INSERT_RUN_LEDGER_SQL,
                employeeId, Date.valueOf(date), kind, amount, notes, runId, userId);
    }

    @Override
    public int deleteRunLedgerEntries(int runId) throws DaoException {
        return executeUpdate(PayrollQuery.DELETE_RUN_LEDGER_SQL, runId);
    }

    @Override
    public int countRunPayments(int runId) throws DaoException {
        List<Integer> counts = readAll(PayrollQuery.COUNT_RUN_PAYMENTS_SQL,
                rs -> rs.getInt(1), runId);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    /**
     * Reads rows of a type other than this DAO's own.
     * <p>
     * {@code queryForObjects} is typed by the DAO's single parameter, and the payroll reads
     * three shapes - a run, a line and a candidate. The same answer {@code usersWhoEntered}
     * arrived at in the statement repository: go through {@code withConnection}, which is the
     * only sanctioned way to hold a connection for the length of one call.
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

    /** One row to one object, for {@link #readAll}. */
    @FunctionalInterface
    private interface RowReader<R> {
        R read(ResultSet rs) throws SQLException;
    }

    private PayrollRun mapRun(ResultSet rs) throws SQLException {
        return new PayrollRun(
                rs.getInt("id"),
                new PayrollPeriod(rs.getInt("period_year"), rs.getInt("period_month")),
                PayrollRunStatus.of(rs.getString("status")),
                rs.getString("notes"),
                dateTime(rs.getTimestamp("date_insert")),
                rs.getInt("user_id"),
                rs.getString("author_name"),
                dateTime(rs.getTimestamp("approved_at")),
                nullableInt(rs, "approved_by"),
                rs.getString("approver_name"),
                dateTime(rs.getTimestamp("paid_at")),
                nullableInt(rs, "paid_by"),
                rs.getString("payer_name"),
                rs.getInt("line_count"),
                rs.getBigDecimal("total_earned"),
                rs.getBigDecimal("total_net"));
    }

    private PayrollLine mapLine(ResultSet rs) throws SQLException {
        return new PayrollLine(
                rs.getInt("id"),
                rs.getInt("payroll_run_id"),
                rs.getInt("employee_id"),
                rs.getString("employee_name"),
                rs.getString("job_name"),
                SalaryKind.orDefault(rs.getString("salary_kind")),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("worked_days"),
                rs.getBigDecimal("absence_days"),
                rs.getBigDecimal("worked_hours"),
                rs.getBigDecimal("basic"),
                rs.getBigDecimal("allowances"),
                rs.getBigDecimal("commission"),
                rs.getBigDecimal("deductions"),
                rs.getBigDecimal("absence_deduction"),
                rs.getBigDecimal("advances_outstanding"),
                rs.getBigDecimal("net_pay"),
                rs.getString("notes"));
    }

    /**
     * A candidate carries no worked days or hours yet: those come from attendance, which is
     * phase D. Until then the screen is where they are typed, and a monthly salary - the
     * ordinary case - needs neither.
     */
    private PayrollInput mapCandidate(ResultSet rs) throws SQLException {
        return new PayrollInput(
                rs.getInt("id"),
                rs.getString("column_name"),
                SalaryKind.orDefault(rs.getString("salary_kind")),
                rs.getBigDecimal("rate"),
                localDate(rs.getDate("hire_date")),
                localDate(rs.getDate("end_date")),
                null, null, null,
                rs.getBigDecimal("allowances"),
                null, null,
                rs.getBigDecimal("advances_outstanding"));
    }

    private static LocalDateTime dateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** {@code DaoList}'s own mapper, which throws {@link DaoException} rather than SQL's. */
    @Override
    public PayrollRun map(ResultSet rs) throws DaoException {
        try {
            return mapRun(rs);
        } catch (SQLException e) {
            throw new DaoException("Could not read a payroll run", e);
        }
    }
}
