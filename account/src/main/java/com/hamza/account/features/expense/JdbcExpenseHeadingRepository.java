package com.hamza.account.features.expense;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The JDBC side of the headings. The statements are {@link ExpenseHeadingQuery}'s. */
public final class JdbcExpenseHeadingRepository extends AbstractDao<ExpenseHeading>
        implements ExpenseHeadingRepository {

    @Override
    public List<ExpenseHeading> all() throws DaoException {
        return queryForObjects(ExpenseHeadingQuery.ALL_SQL, this::map);
    }

    @Override
    public ExpenseHeading find(int id) throws DaoException {
        return queryForObject(ExpenseHeadingQuery.BY_ID_SQL, this::map, id);
    }

    @Override
    public ExpenseHeading bySystemKey(String systemKey) throws DaoException {
        return queryForObject(ExpenseHeadingQuery.BY_SYSTEM_KEY_SQL, this::map, systemKey);
    }

    @Override
    public Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) throws DaoException {
        return withConnection(connection -> {
            Map<Integer, ExpenseHeadingUsage> usage = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(ExpenseHeadingQuery.USAGE_SQL)) {
                statement.setDate(1, Date.valueOf(since));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        int headingId = rs.getInt("type_code");
                        usage.put(headingId, new ExpenseHeadingUsage(headingId, rs.getInt("expense_count"),
                                rs.getBigDecimal("total_since")));
                    }
                }
            }
            return usage;
        });
    }

    @Override
    public boolean nameTaken(String name, int exceptId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ExpenseHeadingQuery.NAME_TAKEN_SQL)) {
                setData(statement, new Object[]{name, exceptId});
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        });
    }

    @Override
    public int insert(ExpenseHeadingDraft draft, int userId) throws DaoException {
        return insertReturningId(ExpenseHeadingQuery.INSERT_SQL, draft.name(), draft.parentId(),
                draft.active(), draft.employeePayment(), userId);
    }

    @Override
    public int update(ExpenseHeadingDraft draft) throws DaoException {
        return executeUpdate(ExpenseHeadingQuery.UPDATE_SQL, draft.name(), draft.parentId(),
                draft.active(), draft.employeePayment(), draft.id());
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate(ExpenseHeadingQuery.DELETE_SQL, id);
    }

    @Override
    public ExpenseHeading map(ResultSet rs) throws DaoException {
        try {
            int parent = rs.getInt("parent_id");
            Integer parentId = rs.wasNull() ? null : parent;
            return new ExpenseHeading(rs.getInt("id"), rs.getString("expenses_name"), parentId,
                    rs.getString("parent_name"), rs.getBoolean("is_active"), rs.getString("system_key"),
                    rs.getBoolean("employee_payment"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
