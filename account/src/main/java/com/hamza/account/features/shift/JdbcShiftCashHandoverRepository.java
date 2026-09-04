package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for close-time cash handover declarations and receipts. */
public final class JdbcShiftCashHandoverRepository implements ShiftCashHandoverRepository {
    private static final String HANDOVER_COLUMNS = """
            SELECT h.id, h.shift_id, h.source_treasury_id, source.t_name source_name,
                   h.target_treasury_id, target.t_name target_name, h.actual_balance,
                   h.retained_float, h.handover_amount, h.handed_by_user_id,
                   cashier.user_name handed_by_name, h.requested_at,
                   r.received_by_user_id, receiver.user_name received_by_name,
                   r.received_at, r.treasury_transfer_id, r.receipt_note
            FROM shift_cash_handovers h
            JOIN treasury source ON source.id=h.source_treasury_id
            JOIN treasury target ON target.id=h.target_treasury_id
            JOIN users cashier ON cashier.id=h.handed_by_user_id
            LEFT JOIN shift_cash_handover_receipts r ON r.handover_id=h.id
            LEFT JOIN users receiver ON receiver.id=r.received_by_user_id
            """;

    @Override
    public List<ShiftCashHandoverPolicy> loadPolicies() throws DaoException {
        String sql = """
                SELECT p.source_treasury_id, source.t_name source_name, p.enabled,
                       p.target_treasury_id, target.t_name target_name, p.retained_float,
                       p.updated_by_user_id, actor.user_name actor_name, p.updated_at
                FROM shift_cash_handover_policy p
                JOIN treasury source ON source.id=p.source_treasury_id
                JOIN treasury target ON target.id=p.target_treasury_id
                JOIN users actor ON actor.id=p.updated_by_user_id
                ORDER BY source.sort_order, source.id
                """;
        return withConnection(connection -> {
            List<ShiftCashHandoverPolicy> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new ShiftCashHandoverPolicy(
                            rows.getInt("source_treasury_id"), rows.getString("source_name"),
                            rows.getBoolean("enabled"), rows.getInt("target_treasury_id"),
                            rows.getString("target_name"), rows.getBigDecimal("retained_float"),
                            rows.getInt("updated_by_user_id"), rows.getString("actor_name"),
                            rows.getTimestamp("updated_at").toLocalDateTime()));
                }
                return List.copyOf(result);
            }
        });
    }

    @Override
    public void savePolicy(int sourceTreasuryId, int targetTreasuryId, BigDecimal retainedFloat,
                           boolean enabled, int actorUserId) throws DaoException {
        String sql = """
                INSERT INTO shift_cash_handover_policy
                    (source_treasury_id, enabled, target_treasury_id, retained_float,
                     updated_by_user_id)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),
                    target_treasury_id=VALUES(target_treasury_id),
                    retained_float=VALUES(retained_float),
                    updated_by_user_id=VALUES(updated_by_user_id),
                    updated_at=CURRENT_TIMESTAMP
                """;
        update(sql, sourceTreasuryId, enabled, targetTreasuryId, retainedFloat, actorUserId);
    }

    @Override
    public int appendForClosedShift(int shiftId, int sourceTreasuryId, BigDecimal actualBalance,
                                    int handedByUserId, LocalDateTime requestedAt) throws DaoException {
        String sql = """
                INSERT INTO shift_cash_handovers
                    (shift_id, source_treasury_id, target_treasury_id, actual_balance,
                     retained_float, handover_amount, handed_by_user_id, requested_at)
                SELECT ?, ?, p.target_treasury_id, ?, p.retained_float,
                       ? - p.retained_float, ?, ?
                FROM shift_cash_handover_policy p
                WHERE p.source_treasury_id=? AND p.enabled=TRUE
                  AND ? > p.retained_float
                """;
        return update(sql, shiftId, sourceTreasuryId, actualBalance, actualBalance,
                handedByUserId, requestedAt, sourceTreasuryId, actualBalance);
    }

    @Override
    public List<ShiftCashHandover> loadPending() throws DaoException {
        String sql = HANDOVER_COLUMNS + " WHERE r.id IS NULL ORDER BY h.requested_at, h.id";
        return withConnection(connection -> {
            List<ShiftCashHandover> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(map(rows));
                return List.copyOf(result);
            }
        });
    }

    @Override
    public ShiftCashHandover findForUpdate(long handoverId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM shift_cash_handovers WHERE id=? FOR UPDATE")) {
                lock.setLong(1, handoverId);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) return null;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    HANDOVER_COLUMNS + " WHERE h.id=?")) {
                statement.setLong(1, handoverId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? map(rows) : null;
                }
            }
        });
    }

    @Override
    public int insertReceipt(long handoverId, int receivedByUserId, LocalDateTime receivedAt,
                             int treasuryTransferId, String note) throws DaoException {
        return update("""
                INSERT INTO shift_cash_handover_receipts
                    (handover_id, received_by_user_id, received_at,
                     treasury_transfer_id, receipt_note)
                VALUES (?, ?, ?, ?, ?)
                """, handoverId, receivedByUserId, receivedAt, treasuryTransferId, note);
    }

    private int update(String sql, Object... values) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
                for (int index = 0; index < values.length; index++) {
                    statement.setObject(index + 1, values[index]);
                }
                return statement.executeUpdate();
            }
        });
    }

    private static ShiftCashHandover map(ResultSet rows) throws SQLException {
        int receivedBy = rows.getInt("received_by_user_id");
        Integer nullableReceivedBy = rows.wasNull() ? null : receivedBy;
        int transferId = rows.getInt("treasury_transfer_id");
        Integer nullableTransferId = rows.wasNull() ? null : transferId;
        var receivedTimestamp = rows.getTimestamp("received_at");
        return new ShiftCashHandover(
                rows.getLong("id"), rows.getInt("shift_id"),
                rows.getInt("source_treasury_id"), rows.getString("source_name"),
                rows.getInt("target_treasury_id"), rows.getString("target_name"),
                rows.getBigDecimal("actual_balance"), rows.getBigDecimal("retained_float"),
                rows.getBigDecimal("handover_amount"), rows.getInt("handed_by_user_id"),
                rows.getString("handed_by_name"), rows.getTimestamp("requested_at").toLocalDateTime(),
                nullableReceivedBy, rows.getString("received_by_name"),
                receivedTimestamp == null ? null : receivedTimestamp.toLocalDateTime(),
                nullableTransferId, rows.getString("receipt_note"));
    }

    private <T> T withConnection(SqlWork<T> work) throws DaoException {
        java.sql.Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not access shift cash handovers", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(java.sql.Connection connection) throws SQLException, DaoException;
    }
}
