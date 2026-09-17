package com.hamza.account.features.expense;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC side of the expenses screens.
 * <p>
 * The statements are {@link ExpenseQuery}'s, and {@link #bindWhere} follows the same {@code if}s in the
 * same sequence as {@link ExpenseQuery#whereSql}. A statement whose parameters are bound in a different
 * order than they are written still runs; it just answers a different question.
 */
public final class JdbcExpenseRepository extends AbstractDao<ExpenseRow> implements ExpenseRepository {

    @Override
    public List<ExpenseRow> search(ExpenseFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindWhere(values, filter);
        values.add(filter.queryLimit());
        values.add(filter.offset());
        return queryForObjects(ExpenseQuery.pageSql(filter), this::map, values.toArray());
    }

    @Override
    public ExpenseSummary summarize(ExpenseFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindWhere(values, filter);
        Object[] parameters = values.toArray();
        return withConnection(connection -> {
            int count;
            java.math.BigDecimal total;
            try (PreparedStatement statement = connection.prepareStatement(ExpenseQuery.summarySql(filter))) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return ExpenseSummary.EMPTY;
                    }
                    count = rs.getInt("expense_count");
                    total = rs.getBigDecimal("total");
                }
            }
            if (count == 0) {
                return ExpenseSummary.EMPTY;
            }
            try (PreparedStatement statement = connection.prepareStatement(ExpenseQuery.topHeadingSql(filter))) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return new ExpenseSummary(count, total, null, null, null);
                    }
                    String parent = rs.getString("parent_heading_name");
                    String name = rs.getString("heading_name");
                    String path = parent == null || parent.isBlank()
                            ? name : parent + ExpenseHeading.PATH_SEPARATOR + name;
                    return new ExpenseSummary(count, total, path, rs.getBigDecimal("total"), null);
                }
            }
        });
    }

    @Override
    public ExpenseRow find(int id) throws DaoException {
        return queryForObject(ExpenseQuery.BY_ID_SQL, this::map, id);
    }

    @Override
    public List<ExpenseUserOption> users() throws DaoException {
        return withConnection(connection -> {
            List<ExpenseUserOption> users = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ExpenseQuery.USERS_SQL);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(new ExpenseUserOption(rs.getInt("id"), rs.getString("user_name")));
                }
            }
            return users;
        });
    }

    @Override
    public List<String> payees(String prefix, int limit) throws DaoException {
        return withConnection(connection -> {
            List<String> payees = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ExpenseQuery.PAYEES_SQL)) {
                setData(statement, new Object[]{ExpenseQuery.startsPattern(prefix), limit});
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        payees.add(rs.getString(1));
                    }
                }
            }
            return payees;
        });
    }

    @Override
    public int insert(ExpenseEntry entry, Integer employeeId, Integer shiftId, int userId) throws DaoException {
        return insertReturningId(ExpenseQuery.INSERT_SQL, entry.headingId(), Date.valueOf(entry.date()),
                entry.amount(), entry.notes(), employeeId, entry.treasuryId(), userId, shiftId,
                entry.payee(), entry.referenceNo());
    }

    @Override
    public int update(ExpenseEntry entry) throws DaoException {
        return executeUpdate(ExpenseQuery.UPDATE_SQL, entry.headingId(), Date.valueOf(entry.date()),
                entry.amount(), entry.notes(), entry.treasuryId(), entry.payee(), entry.referenceNo(),
                entry.id());
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate(ExpenseQuery.DELETE_SQL, id);
    }

    /** Every condition {@link ExpenseQuery#whereSql} writes, in the order it writes them. */
    static void bindWhere(List<Object> values, ExpenseFilter filter) {
        values.addAll(ExpenseQuery.whereValues(filter));
    }

    @Override
    public ExpenseRow map(ResultSet rs) throws DaoException {
        try {
            // wasNull() answers for the most recent getter, so each nullable id is judged on the spot.
            int employee = rs.getInt("emp_id");
            Integer employeeId = rs.wasNull() ? null : employee;
            int shift = rs.getInt("shift_id");
            Integer shiftId = rs.wasNull() ? null : shift;
            Timestamp entered = rs.getTimestamp("date_insert");
            return new ExpenseRow(
                    rs.getInt("id"),
                    rs.getDate("date").toLocalDate(),
                    rs.getInt("type_code"),
                    rs.getString("heading_name"),
                    rs.getString("parent_heading_name"),
                    rs.getBoolean("employee_payment"),
                    rs.getInt("treasury_id"),
                    rs.getString("treasury_name"),
                    rs.getBigDecimal("amount"),
                    rs.getString("payee"),
                    rs.getString("reference_no"),
                    rs.getString("notes"),
                    employeeId,
                    rs.getString("employee_name"),
                    rs.getInt("user_id"),
                    rs.getString("user_name"),
                    shiftId,
                    entered == null ? null : entered.toLocalDateTime());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
