package com.hamza.account.features.employee;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC side of the employees screens.
 * <p>
 * The statements are {@link EmployeeQuery}'s and the binding order is the order that class
 * writes its conditions in - {@link #bindWhere} and {@link #bindOrder} follow the same
 * {@code if}s in the same sequence, which is the property {@code EmployeeQueryTest}'s parameter
 * counts check. A statement whose parameters are bound in a different order than they are
 * written still runs; it just answers a different question.
 */
public final class JdbcEmployeeRepository extends AbstractDao<Employee> implements EmployeeRepository {

    @Override
    public List<Employee> search(EmployeeFilter filter, boolean salaryVisible) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindWhere(values, filter);
        bindOrder(values, filter);
        values.add(filter.queryLimit());
        values.add(filter.offset());
        return queryForObjects(EmployeeQuery.pageSql(filter, salaryVisible), this::map, values.toArray());
    }

    @Override
    public EmployeeSummary summarize(EmployeeFilter filter, boolean salaryVisible) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindWhere(values, filter);
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeQuery.summarySql(filter, salaryVisible))) {
                setData(statement, values.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return EmployeeSummary.EMPTY;
                    }
                    return new EmployeeSummary(rs.getInt("employees"), rs.getInt("active_employees"),
                            rs.getInt("delegates"), rs.getBigDecimal("monthly_payroll"));
                }
            }
        });
    }

    @Override
    public Employee find(int id, boolean salaryVisible) throws DaoException {
        return queryForObject(EmployeeQuery.byIdSql(salaryVisible), this::map, id);
    }

    @Override
    public byte[] photo(int id) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(EmployeeQuery.PHOTO_SQL)) {
                statement.setInt(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getBytes(1) : null;
                }
            }
        });
    }

    @Override
    public List<String> names(EmployeeScope scope, boolean delegatesOnly) throws DaoException {
        return withConnection(connection -> {
            List<String> names = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeQuery.namesSql(scope, delegatesOnly));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            return names;
        });
    }

    @Override
    public List<EmployeeRef> refs(EmployeeScope scope, boolean delegatesOnly) throws DaoException {
        return withConnection(connection -> {
            List<EmployeeRef> refs = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeQuery.lookupSql(scope, delegatesOnly));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    refs.add(new EmployeeRef(rs.getInt(1), rs.getString(2)));
                }
            }
            return refs;
        });
    }

    @Override
    public EmployeeRef refByName(String name) throws DaoException {
        return ref(EmployeeQuery.LOOKUP_BY_NAME_SQL, name);
    }

    @Override
    public EmployeeRef refById(int id) throws DaoException {
        return ref(EmployeeQuery.LOOKUP_BY_ID_SQL, id);
    }

    private EmployeeRef ref(String sql, Object key) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, new Object[]{key});
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? new EmployeeRef(rs.getInt(1), rs.getString(2)) : null;
                }
            }
        });
    }

    @Override
    public boolean nameTaken(String name, int exceptId) throws DaoException {
        return scalar(EmployeeQuery.NAME_TAKEN_SQL, name, exceptId) > 0;
    }

    @Override
    public int insert(EmployeeDraft draft, int userId) throws DaoException {
        return insertReturningId(EmployeeQuery.INSERT_SQL,
                draft.name(), draft.jobId(), date(draft.birthDate()), date(draft.hireDate()),
                date(draft.endDate()), draft.rate(), draft.email(), draft.phone(), draft.address(),
                draft.nationalId(), draft.employmentType().name(), draft.defaultTreasuryId(),
                draft.notes(), true, userId);
    }

    @Override
    public int update(EmployeeDraft draft) throws DaoException {
        return executeUpdate(EmployeeQuery.UPDATE_SQL,
                draft.name(), draft.jobId(), date(draft.birthDate()), date(draft.hireDate()),
                date(draft.endDate()), draft.email(), draft.phone(), draft.address(),
                draft.nationalId(), draft.employmentType().name(), draft.defaultTreasuryId(),
                draft.notes(), draft.id());
    }

    @Override
    public int setActive(int id, boolean active) throws DaoException {
        return executeUpdate(EmployeeQuery.SET_ACTIVE_SQL, active, id);
    }

    @Override
    public int updatePhoto(int id, byte[] photo) throws DaoException {
        return executeUpdate(EmployeeQuery.UPDATE_IMAGE_SQL, photo, id);
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate("DELETE FROM employees WHERE id = ?", id);
    }

    @Override
    public List<EmployeeCompensation> compensationHistory(int employeeId) throws DaoException {
        return withConnection(connection -> {
            List<EmployeeCompensation> history = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeQuery.COMPENSATION_HISTORY_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        history.add(new EmployeeCompensation(rs.getInt("id"), rs.getInt("employee_id"),
                                rs.getDate("effective_from").toLocalDate(),
                                SalaryKind.of(rs.getString("salary_kind")),
                                rs.getBigDecimal("rate"), rs.getString("notes")));
                    }
                }
            }
            return history;
        });
    }

    @Override
    public int compensationCount(int employeeId) throws DaoException {
        return scalar(EmployeeQuery.COMPENSATION_COUNT_SQL, employeeId);
    }

    /**
     * An {@code UPDATE} first, an {@code INSERT} when it moved nothing.
     * <p>
     * Not {@code INSERT ... ON DUPLICATE KEY UPDATE}: that reports one affected row for an
     * insert and two for an update, so a caller checking the count for success cannot tell
     * "written" from "written twice", and the number reaching the screen would be wrong in one
     * of the two cases. Both paths here answer 1.
     */
    @Override
    public int saveCompensation(int employeeId, LocalDate effectiveFrom, SalaryKind kind,
                                BigDecimal rate, String notes, int userId) throws DaoException {
        int updated = executeUpdate(EmployeeQuery.UPDATE_COMPENSATION_SQL,
                kind.name(), rate, notes, employeeId, date(effectiveFrom));
        if (updated > 0) {
            return 1;
        }
        return executeUpdate(EmployeeQuery.INSERT_COMPENSATION_SQL,
                employeeId, date(effectiveFrom), kind.name(), rate, notes, userId);
    }

    @Override
    public int deleteCompensation(int employeeId, int compensationId) throws DaoException {
        return executeUpdate(EmployeeQuery.DELETE_COMPENSATION_SQL, compensationId, employeeId);
    }

    @Override
    public int updateHireRate(int employeeId, BigDecimal rate) throws DaoException {
        return executeUpdate(EmployeeQuery.UPDATE_HIRE_SALARY_SQL, rate, employeeId);
    }

    /** Every condition {@link EmployeeQuery#whereSql} writes, in the order it writes them. */
    private static void bindWhere(List<Object> values, EmployeeFilter filter) {
        if (filter.hasText()) {
            String contains = EmployeeQuery.containsPattern(filter.text());
            values.add(contains);
            values.add(contains);
            values.add(contains);
            values.add(filter.numericText());
        }
        if (filter.jobId() != null) {
            values.add(filter.jobId());
        }
        if (filter.state().active() != null) {
            values.add(filter.state().active());
        }
        if (filter.salaryKind() != null) {
            values.add(filter.salaryKind().name());
        }
        if (filter.employmentType() != null) {
            values.add(filter.employmentType().name());
        }
        if (filter.hiredFrom() != null) {
            values.add(date(filter.hiredFrom()));
        }
        if (filter.hiredTo() != null) {
            values.add(date(filter.hiredTo()));
        }
        if (filter.minRate() != null) {
            values.add(filter.minRate());
        }
        if (filter.maxRate() != null) {
            values.add(filter.maxRate());
        }
    }

    /** The relevance ranking, which only exists when there is text to rank against. */
    private static void bindOrder(List<Object> values, EmployeeFilter filter) {
        if (filter.hasText()) {
            values.add(filter.numericText());
            values.add(filter.text());
            values.add(EmployeeQuery.startsPattern(filter.text()));
        }
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private int scalar(String sql, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public Employee map(ResultSet rs) throws DaoException {
        try {
            Timestamp created = rs.getTimestamp("date_insert");
            String kind = rs.getString("salary_kind");
            // Read and judged on the spot: wasNull() answers for the most recent getter, and
            // the constructor call below reads half a dozen more columns before it would be asked.
            int treasury = rs.getInt("default_treasury_id");
            Integer defaultTreasury = rs.wasNull() || treasury == 0 ? null : treasury;
            return new Employee(
                    rs.getInt("id"),
                    rs.getString("column_name"),
                    rs.getInt("job"),
                    rs.getString("job_name"),
                    rs.getBoolean("is_delegate"),
                    localDate(rs.getDate("birth_date")),
                    localDate(rs.getDate("hire_date")),
                    localDate(rs.getDate("end_date")),
                    rs.getBoolean("is_active"),
                    EmploymentType.orDefault(rs.getString("employment_type")),
                    rs.getString("national_id"),
                    rs.getString("email"),
                    rs.getString("tel"),
                    rs.getString("address"),
                    rs.getString("notes"),
                    defaultTreasury,
                    rs.getBigDecimal("hire_salary"),
                    kind == null ? null : SalaryKind.of(kind),
                    rs.getBigDecimal("current_rate"),
                    localDate(rs.getDate("rate_from")),
                    created == null ? null : created.toLocalDateTime());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
