package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only, parameterized query side of the append-only shift journal. */
public final class ShiftCashLedgerQueryDao extends AbstractDao<ShiftCashLedgerEntry> {

    public List<ShiftCashLedgerEntry> search(ShiftCashLedgerFilter filter) throws DaoException {
        StringBuilder sql = new StringBuilder("""
                SELECT l.*, t.t_name AS treasury_name, u.user_name AS actor_username
                FROM shift_cash_ledger l
                JOIN treasury t ON t.id=l.treasury_id
                JOIN users u ON u.id=l.actor_user_id
                WHERE l.shift_id=?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(filter.shiftId());
        if (filter.action() != null) {
            sql.append(" AND l.action_type=?");
            parameters.add(filter.action().name());
        }
        if (filter.source() != null) {
            sql.append(" AND l.source_type=?");
            parameters.add(filter.source().code());
        }
        if (filter.sourceId() != null) {
            sql.append(" AND l.source_id=?");
            parameters.add(filter.sourceId());
        }
        sql.append(" ORDER BY l.id DESC LIMIT ?");
        parameters.add(filter.limit());
        return queryForObjects(sql.toString(), this::map, parameters.toArray());
    }

    @Override
    public ShiftCashLedgerEntry map(ResultSet rs) throws DaoException {
        try {
            int sourceCode = rs.getInt("source_type");
            ShiftCashSource source = java.util.Arrays.stream(ShiftCashSource.values())
                    .filter(value -> value.code() == sourceCode)
                    .findFirst()
                    .orElseThrow(() -> new SQLException("Unknown shift cash source: " + sourceCode));
            return new ShiftCashLedgerEntry(
                    rs.getLong("id"), rs.getInt("shift_id"),
                    rs.getObject("origin_shift_id", Integer.class), rs.getInt("treasury_id"),
                    rs.getString("treasury_name"), rs.getInt("actor_user_id"),
                    rs.getString("actor_username"), source, rs.getInt("source_id"),
                    ShiftLedgerAction.valueOf(rs.getString("action_type")),
                    rs.getBigDecimal("income_delta"), rs.getBigDecimal("output_delta"),
                    rs.getString("reason"), rs.getTimestamp("occurred_at").toLocalDateTime());
        } catch (SQLException | IllegalArgumentException e) {
            throw new DaoException("Could not map shift cash journal row", e);
        }
    }

    @Override public List<ShiftCashLedgerEntry> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(ShiftCashLedgerEntry value) { throw new UnsupportedOperationException(); }
    @Override public int update(ShiftCashLedgerEntry value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public ShiftCashLedgerEntry getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(ShiftCashLedgerEntry value) { throw new UnsupportedOperationException(); }
}
