package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for explicit cashier-to-till access. */
public final class JdbcCashierTreasuryAssignmentRepository
        implements CashierTreasuryAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT a.id, a.user_id, u.user_name, a.treasury_id, t.t_name,
                   a.can_open_shift, a.is_default, a.active, a.assigned_by,
                   creator.user_name assigned_by_name, a.assigned_at, a.updated_by,
                   updater.user_name updated_by_name, a.updated_at
            FROM cashier_treasury_assignment a
            JOIN users u ON u.id=a.user_id
            JOIN treasury t ON t.id=a.treasury_id
            JOIN users creator ON creator.id=a.assigned_by
            JOIN users updater ON updater.id=a.updated_by
            """;

    @Override
    public List<CashierTreasuryAssignment> loadAll() throws DaoException {
        String sql = SELECT_COLUMNS + " ORDER BY a.active DESC, u.user_name, a.is_default DESC, t.sort_order, t.id";
        return withConnection(connection -> {
            List<CashierTreasuryAssignment> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(map(rows));
                return List.copyOf(result);
            }
        });
    }

    @Override
    public List<CashierTreasuryAssignmentEvent> loadHistory(int limit) throws DaoException {
        String sql = """
                SELECT id, assignment_id, user_id, user_name_snapshot, treasury_id,
                       treasury_name_snapshot, action_type, before_can_open_shift,
                       after_can_open_shift, before_is_default, after_is_default,
                       before_active, after_active, actor_user_id, actor_name_snapshot,
                       occurred_at
                FROM cashier_treasury_assignment_events
                ORDER BY id DESC
                LIMIT ?
                """;
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return withConnection(connection -> {
            List<CashierTreasuryAssignmentEvent> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, safeLimit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(mapEvent(rows));
                }
                return List.copyOf(result);
            }
        });
    }

    @Override
    public List<CashierTreasuryChoice> availableTreasuries(int userId, boolean enforceAssignments)
            throws DaoException {
        String sql = """
                SELECT t.id, t.t_name, COALESCE(a.is_default, FALSE) is_default
                FROM treasury t
                JOIN shift_treasury_policy p ON p.treasury_id=t.id
                                              AND p.tracking_mode <> 'NONE'
                LEFT JOIN cashier_treasury_assignment a ON a.treasury_id=t.id
                                                       AND a.user_id=?
                                                       AND a.active=TRUE
                                                       AND a.can_open_shift=TRUE
                WHERE t.is_active=TRUE AND (?=FALSE OR a.id IS NOT NULL)
                ORDER BY COALESCE(a.is_default, FALSE) DESC, t.sort_order, t.id
                """;
        return withConnection(connection -> {
            List<CashierTreasuryChoice> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                statement.setBoolean(2, enforceAssignments);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new CashierTreasuryChoice(
                                rows.getInt("id"), rows.getString("t_name"), rows.getBoolean("is_default")));
                    }
                }
                return List.copyOf(result);
            }
        });
    }

    @Override
    public CashierTreasuryAssignment findById(int assignmentId, boolean forUpdate) throws DaoException {
        String sql = SELECT_COLUMNS + " WHERE a.id=?" + (forUpdate ? " FOR UPDATE" : "");
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, assignmentId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? map(rows) : null;
                }
            }
        });
    }

    @Override
    public boolean canOpenShift(int userId, int treasuryId) throws DaoException {
        return exists("""
                SELECT EXISTS(SELECT 1 FROM cashier_treasury_assignment
                              WHERE user_id=? AND treasury_id=? AND active=TRUE
                                AND can_open_shift=TRUE)
                """, userId, treasuryId);
    }

    @Override
    public boolean isAssignable(int userId, int treasuryId) throws DaoException {
        return exists("""
                SELECT EXISTS(SELECT 1 FROM users u JOIN treasury t ON t.id=?
                              JOIN shift_treasury_policy p ON p.treasury_id=t.id
                              WHERE u.id=? AND u.user_activity=TRUE AND t.is_active=TRUE
                                AND p.tracking_mode <> 'NONE')
                """, treasuryId, userId);
    }

    @Override
    public boolean hasOpenShift(int userId, int treasuryId) throws DaoException {
        return exists("""
                SELECT EXISTS(SELECT 1 FROM user_shifts
                              WHERE user_id=? AND treasury_id=? AND is_open=TRUE)
                """, userId, treasuryId);
    }

    @Override
    public boolean hasActiveAssignments() throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM cashier_treasury_assignment WHERE active=TRUE)" );
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        });
    }

    @Override
    public void lockUser(int userId) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM users WHERE id=? FOR UPDATE")) {
                statement.setInt(1, userId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new DaoException("Cashier does not exist");
                }
                return null;
            }
        });
    }

    @Override
    public void clearDefault(int userId, int actorUserId) throws DaoException {
        update("""
                UPDATE cashier_treasury_assignment
                SET is_default=FALSE, updated_by=?, updated_at=CURRENT_TIMESTAMP
                WHERE user_id=? AND is_default=TRUE
                """, actorUserId, userId);
    }

    @Override
    public void upsert(int userId, int treasuryId, boolean defaultTreasury, int actorUserId)
            throws DaoException {
        update("""
                INSERT INTO cashier_treasury_assignment
                    (user_id, treasury_id, can_open_shift, is_default, active,
                     assigned_by, updated_by)
                VALUES (?, ?, TRUE, ?, TRUE, ?, ?)
                ON DUPLICATE KEY UPDATE can_open_shift=TRUE, is_default=VALUES(is_default),
                                        active=TRUE, updated_by=VALUES(updated_by),
                                        updated_at=CURRENT_TIMESTAMP
                """, userId, treasuryId, defaultTreasury, actorUserId, actorUserId);
    }

    @Override
    public int deactivate(int assignmentId, int actorUserId) throws DaoException {
        return update("""
                UPDATE cashier_treasury_assignment
                SET active=FALSE, can_open_shift=FALSE, is_default=FALSE,
                    updated_by=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND active=TRUE
                """, actorUserId, assignmentId);
    }

    private boolean exists(String sql, int first, int second) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, first);
                statement.setInt(2, second);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() && rows.getBoolean(1);
                }
            }
        });
    }

    private int update(String sql, Object... values) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) {
                    statement.setObject(index + 1, values[index]);
                }
                return statement.executeUpdate();
            }
        });
    }

    private static CashierTreasuryAssignment map(ResultSet rows) throws SQLException {
        return new CashierTreasuryAssignment(
                rows.getInt("id"), rows.getInt("user_id"), rows.getString("user_name"),
                rows.getInt("treasury_id"), rows.getString("t_name"),
                rows.getBoolean("can_open_shift"), rows.getBoolean("is_default"),
                rows.getBoolean("active"), rows.getInt("assigned_by"),
                rows.getString("assigned_by_name"), rows.getTimestamp("assigned_at").toLocalDateTime(),
                rows.getInt("updated_by"), rows.getString("updated_by_name"),
                rows.getTimestamp("updated_at").toLocalDateTime());
    }

    private static CashierTreasuryAssignmentEvent mapEvent(ResultSet rows) throws SQLException {
        return new CashierTreasuryAssignmentEvent(
                rows.getLong("id"), rows.getInt("assignment_id"), rows.getInt("user_id"),
                rows.getString("user_name_snapshot"), rows.getInt("treasury_id"),
                rows.getString("treasury_name_snapshot"),
                CashierTreasuryAssignmentEvent.Action.valueOf(rows.getString("action_type")),
                nullableBoolean(rows, "before_can_open_shift"), rows.getBoolean("after_can_open_shift"),
                nullableBoolean(rows, "before_is_default"), rows.getBoolean("after_is_default"),
                nullableBoolean(rows, "before_active"), rows.getBoolean("after_active"),
                rows.getInt("actor_user_id"), rows.getString("actor_name_snapshot"),
                rows.getTimestamp("occurred_at").toLocalDateTime());
    }

    private static Boolean nullableBoolean(ResultSet rows, String column) throws SQLException {
        boolean value = rows.getBoolean(column);
        return rows.wasNull() ? null : value;
    }

    private <T> T withConnection(SqlWork<T> work) throws DaoException {
        java.sql.Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not access cashier treasury assignments", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(java.sql.Connection connection) throws SQLException, DaoException;
    }
}
