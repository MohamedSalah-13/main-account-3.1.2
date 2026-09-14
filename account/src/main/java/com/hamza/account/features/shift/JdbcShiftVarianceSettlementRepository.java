package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** JDBC store for immutable deferred variance requests. */
public final class JdbcShiftVarianceSettlementRepository extends AbstractDao<ShiftVarianceSettlement>
        implements ShiftVarianceSettlementRepository {

    private static final String SELECT = """
            SELECT r.id, r.shift_id, r.treasury_id, t.t_name treasury_name,
                   r.expected_balance, r.actual_balance, r.difference_amount,
                   r.original_shift_date, r.requested_by_user_id,
                   u.user_name requested_by_name, r.requested_at
            FROM shift_variance_settlement_requests r
            JOIN treasury t ON t.id=r.treasury_id
            JOIN users u ON u.id=r.requested_by_user_id
            LEFT JOIN shift_cash_variance_adjustments a ON a.shift_id=r.shift_id
            """;

    @Override
    public int append(int shiftId, int treasuryId, java.math.BigDecimal expected,
                      java.math.BigDecimal actual, java.math.BigDecimal difference,
                      java.time.LocalDate originalShiftDate, int actorUserId,
                      LocalDateTime requestedAt) throws DaoException {
        return executeUpdate("""
                INSERT INTO shift_variance_settlement_requests
                    (shift_id, treasury_id, expected_balance, actual_balance,
                     difference_amount, original_shift_date, requested_by_user_id, requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, shiftId, treasuryId, expected, actual, difference, originalShiftDate,
                actorUserId, requestedAt);
    }

    @Override
    public List<ShiftVarianceSettlement> loadPending() throws DaoException {
        return withConnection(connection -> {
            List<ShiftVarianceSettlement> result = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    SELECT + " WHERE a.id IS NULL ORDER BY r.requested_at, r.id");
                 var rows = statement.executeQuery()) {
                while (rows.next()) result.add(mapRow(rows));
            }
            return List.copyOf(result);
        });
    }

    @Override
    public ShiftVarianceSettlement findPendingForUpdate(long requestId) throws DaoException {
        return withConnection(connection -> {
            try (var lock = connection.prepareStatement(
                    "SELECT id FROM shift_variance_settlement_requests WHERE id=? FOR UPDATE")) {
                lock.setLong(1, requestId);
                try (var rows = lock.executeQuery()) {
                    if (!rows.next()) return null;
                }
            }
            try (var statement = connection.prepareStatement(
                    SELECT + " WHERE r.id=? AND a.id IS NULL")) {
                statement.setLong(1, requestId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? mapRow(rows) : null;
                }
            }
        });
    }

    @Override
    public boolean isPendingForShift(int shiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT EXISTS(
                        SELECT 1 FROM shift_variance_settlement_requests r
                        LEFT JOIN shift_cash_variance_adjustments a ON a.shift_id=r.shift_id
                        WHERE r.shift_id=? AND a.id IS NULL)
                    """)) {
                statement.setInt(1, shiftId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() && rows.getBoolean(1);
                }
            }
        });
    }

    private static ShiftVarianceSettlement mapRow(ResultSet rows) throws SQLException {
        return new ShiftVarianceSettlement(rows.getLong("id"), rows.getInt("shift_id"),
                rows.getInt("treasury_id"), rows.getString("treasury_name"),
                rows.getBigDecimal("expected_balance"), rows.getBigDecimal("actual_balance"),
                rows.getBigDecimal("difference_amount"), rows.getDate("original_shift_date").toLocalDate(),
                rows.getInt("requested_by_user_id"), rows.getString("requested_by_name"),
                rows.getTimestamp("requested_at").toLocalDateTime());
    }
}
