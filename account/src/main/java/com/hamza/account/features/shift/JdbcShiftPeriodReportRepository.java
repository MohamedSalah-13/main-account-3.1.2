package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** SQL projection for the period report; no JavaFX model or live-balance recalculation. */
public final class JdbcShiftPeriodReportRepository extends AbstractDao<Object>
        implements ShiftPeriodReportRepository {

    @Override
    public List<ShiftPeriodRow> search(ShiftPeriodQuery query) throws DaoException {
        StringBuilder sql = new StringBuilder("""
                SELECT us.user_id, COALESCE(u.user_name, '') username,
                       us.treasury_id, COALESCE(t.t_name, '') treasury_name,
                       COUNT(*) shift_count,
                       SUM(CASE WHEN us.is_open THEN 1 ELSE 0 END) open_shift_count,
                       SUM(CASE WHEN us.is_open THEN 0 ELSE 1 END) closed_shift_count,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.total_sales END), 0) total_sales,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.total_sales_returns END), 0) total_sales_returns,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.total_expenses END), 0) total_expenses,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.total_deposits END), 0) total_deposits,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.total_withdrawals END), 0) total_withdrawals,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.expected_balance END), 0) total_expected_balance,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.close_balance END), 0) total_actual_balance,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.difference END), 0) total_difference,
                       COALESCE(SUM(CASE WHEN us.is_open THEN 0 ELSE us.invoices_count END), 0) invoices_count
                FROM user_shifts us
                LEFT JOIN users u ON u.id=us.user_id
                LEFT JOIN treasury t ON t.id=us.treasury_id
                WHERE us.open_time>=? AND us.open_time<?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.valueOf(query.fromInclusive()));
        parameters.add(Timestamp.valueOf(query.toExclusive()));
        if (query.userId() != null) {
            sql.append(" AND us.user_id=?");
            parameters.add(query.userId());
        }
        if (query.treasuryId() != null) {
            sql.append(" AND us.treasury_id=?");
            parameters.add(query.treasuryId());
        }
        sql.append(" GROUP BY us.user_id, u.user_name, us.treasury_id, t.t_name")
                .append(" ORDER BY u.user_name, t.t_name");

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<ShiftPeriodRow> result = new ArrayList<>();
                    while (rows.next()) result.add(mapRow(rows));
                    return result;
                }
            } catch (SQLException error) {
                throw new DaoException("Could not load the shift period report", error);
            }
        });
    }

    private static ShiftPeriodRow mapRow(ResultSet rows) throws SQLException {
        return new ShiftPeriodRow(
                rows.getInt("user_id"), rows.getString("username"),
                rows.getInt("treasury_id"), rows.getString("treasury_name"),
                rows.getLong("shift_count"), rows.getLong("open_shift_count"),
                rows.getLong("closed_shift_count"), rows.getBigDecimal("total_sales"),
                rows.getBigDecimal("total_sales_returns"), rows.getBigDecimal("total_expenses"),
                rows.getBigDecimal("total_deposits"), rows.getBigDecimal("total_withdrawals"),
                rows.getBigDecimal("total_expected_balance"), rows.getBigDecimal("total_actual_balance"),
                rows.getBigDecimal("total_difference"), rows.getLong("invoices_count"));
    }

    @Override public List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataByString(String value) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(ResultSet rows) { throw new UnsupportedOperationException(); }
}
