package com.hamza.account.features.expense.budget;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The JDBC side of the budgets. Every statement is {@link ExpenseBudgetQuery}'s. */
public final class JdbcExpenseBudgetRepository extends AbstractDao<ExpenseBudget>
        implements ExpenseBudgetRepository {

    @Override
    public List<ExpenseBudget> byYear(int year) throws DaoException {
        return queryForObjects(ExpenseBudgetQuery.BY_YEAR_SQL, this::map, year);
    }

    @Override
    public List<ExpenseBudget> forPeriod(LocalDate from, LocalDate to) throws DaoException {
        int firstMonth = from.getYear() * 100 + from.getMonthValue();
        int lastMonth = to.getYear() * 100 + to.getMonthValue();
        return queryForObjects(ExpenseBudgetQuery.FOR_PERIOD_SQL, this::map,
                from.getYear(), to.getYear(), firstMonth, lastMonth);
    }

    @Override
    public ExpenseBudget find(int id) throws DaoException {
        return queryForObject(ExpenseBudgetQuery.BY_ID_SQL, this::map, id);
    }

    @Override
    public boolean taken(int headingId, int year, Integer month, int exceptId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ExpenseBudgetQuery.TAKEN_SQL)) {
                setData(statement, new Object[]{headingId, year, month, exceptId});
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() && rows.getInt(1) > 0;
                }
            }
        });
    }

    @Override
    public List<Integer> years() throws DaoException {
        return withConnection(connection -> {
            List<Integer> years = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ExpenseBudgetQuery.YEARS_SQL);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    years.add(rows.getInt(1));
                }
            }
            return years;
        });
    }

    @Override
    public int insert(ExpenseBudgetDraft draft, int userId) throws DaoException {
        return insertReturningId(ExpenseBudgetQuery.INSERT_SQL, draft.headingId(), draft.year(), draft.month(),
                draft.amount(), draft.notes(), userId);
    }

    @Override
    public int update(ExpenseBudgetDraft draft) throws DaoException {
        return executeUpdate(ExpenseBudgetQuery.UPDATE_SQL, draft.amount(), draft.notes(), draft.id());
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate(ExpenseBudgetQuery.DELETE_SQL, id);
    }

    @Override
    public ExpenseBudget map(ResultSet rs) throws DaoException {
        try {
            // wasNull() answers for the most recent getter: a yearly budget stores no month.
            int month = rs.getInt("month");
            Integer monthOrNull = rs.wasNull() ? null : month;
            BigDecimal amount = rs.getBigDecimal("amount");
            return new ExpenseBudget(rs.getInt("id"), rs.getInt("heading_id"), rs.getString("heading_name"),
                    rs.getString("parent_heading_name"), rs.getInt("year"), monthOrNull, amount,
                    rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
