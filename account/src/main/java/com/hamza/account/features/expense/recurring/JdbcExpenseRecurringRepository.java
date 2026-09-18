package com.hamza.account.features.expense.recurring;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The JDBC side of the recurring templates. Every statement is {@link ExpenseRecurringQuery}'s. */
public final class JdbcExpenseRecurringRepository extends AbstractDao<ExpenseRecurring>
        implements ExpenseRecurringRepository {

    @Override
    public List<ExpenseRecurring> all() throws DaoException {
        return queryForObjects(ExpenseRecurringQuery.ALL_SQL, this::map);
    }

    @Override
    public List<ExpenseRecurring> active() throws DaoException {
        return queryForObjects(ExpenseRecurringQuery.ACTIVE_SQL, this::map);
    }

    @Override
    public ExpenseRecurring find(int id) throws DaoException {
        return queryForObject(ExpenseRecurringQuery.BY_ID_SQL, this::map, id);
    }

    /**
     * Each recorded expense's date is filed into the period of its own template, because a quarter
     * starts where its template starts. The templates are read first for that reason.
     */
    @Override
    public Map<Integer, Set<LocalDate>> recordedPeriods(LocalDate since) throws DaoException {
        Map<Integer, ExpenseRecurring> byId = new HashMap<>();
        for (ExpenseRecurring template : active()) {
            byId.put(template.id(), template);
        }
        return withConnection(connection -> {
            Map<Integer, Set<LocalDate>> periods = new HashMap<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(ExpenseRecurringQuery.RECORDED_PERIODS_SQL)) {
                setData(statement, new Object[]{Date.valueOf(since)});
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        int templateId = rows.getInt("recurring_id");
                        ExpenseRecurring template = byId.get(templateId);
                        if (template == null) {
                            continue;
                        }
                        LocalDate day = rows.getDate("date").toLocalDate();
                        periods.computeIfAbsent(templateId, key -> new HashSet<>())
                                .add(template.periodStart(day));
                    }
                }
            }
            return periods;
        });
    }

    @Override
    public int recordedCount(int id) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(ExpenseRecurringQuery.RECORDED_COUNT_SQL)) {
                setData(statement, new Object[]{id});
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public int insert(ExpenseRecurringDraft draft, int userId) throws DaoException {
        return insertReturningId(ExpenseRecurringQuery.INSERT_SQL, draft.headingId(), draft.treasuryId(),
                draft.amount(), draft.payee(), draft.notes(), draft.frequency().name(), draft.dayOfMonth(),
                Date.valueOf(draft.startDate()), draft.endDate() == null ? null : Date.valueOf(draft.endDate()),
                draft.active() ? 1 : 0, userId);
    }

    @Override
    public int update(ExpenseRecurringDraft draft) throws DaoException {
        return executeUpdate(ExpenseRecurringQuery.UPDATE_SQL, draft.headingId(), draft.treasuryId(),
                draft.amount(), draft.payee(), draft.notes(), draft.frequency().name(), draft.dayOfMonth(),
                Date.valueOf(draft.startDate()), draft.endDate() == null ? null : Date.valueOf(draft.endDate()),
                draft.active() ? 1 : 0, draft.id());
    }

    @Override
    public int delete(int id) throws DaoException {
        return executeUpdate(ExpenseRecurringQuery.DELETE_SQL, id);
    }

    @Override
    public ExpenseRecurring map(ResultSet rs) throws DaoException {
        try {
            Date end = rs.getDate("end_date");
            return new ExpenseRecurring(rs.getInt("id"), rs.getInt("heading_id"), rs.getString("heading_name"),
                    rs.getString("parent_heading_name"), rs.getInt("treasury_id"), rs.getString("treasury_name"),
                    rs.getBigDecimal("amount"), rs.getString("payee"), rs.getString("notes"),
                    ExpenseFrequency.valueOf(rs.getString("frequency")), rs.getInt("day_of_month"),
                    rs.getDate("start_date").toLocalDate(), end == null ? null : end.toLocalDate(),
                    rs.getBoolean("is_active"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
