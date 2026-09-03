package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC persistence for the singleton policy and per-treasury participation. */
public final class JdbcShiftPolicyRepository implements ShiftPolicyRepository {

    @Override
    public ShiftPolicy load() throws DaoException {
        String sql = "SELECT mode, blind_close, auto_print_z, variance_tolerance, "
                + "require_variance_reason, require_supervisor_approval, enforce_treasury_assignments "
                + "FROM shift_policy WHERE id = 1";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return ShiftPolicy.DISABLED;
                return new ShiftPolicy(
                        ShiftMode.valueOf(rs.getString("mode")),
                        rs.getBoolean("blind_close"),
                        rs.getBoolean("auto_print_z"),
                        rs.getBigDecimal("variance_tolerance"),
                        rs.getBoolean("require_variance_reason"),
                        rs.getBoolean("require_supervisor_approval"),
                        rs.getBoolean("enforce_treasury_assignments"));
            } catch (SQLException | IllegalArgumentException e) {
                throw new DaoException("Could not load the shift policy", e);
            }
        });
    }

    @Override
    public List<TreasuryShiftPolicy> loadTreasuries() throws DaoException {
        String sql = "SELECT t.id, t.t_name, COALESCE(stp.tracking_mode, 'NONE') tracking_mode "
                + "FROM treasury t LEFT JOIN shift_treasury_policy stp ON stp.treasury_id = t.id "
                + "ORDER BY t.id";
        return withConnection(connection -> {
            List<TreasuryShiftPolicy> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new TreasuryShiftPolicy(rs.getInt(1), rs.getString(2),
                            ShiftTrackingMode.valueOf(rs.getString(3))));
                }
                return result;
            } catch (SQLException | IllegalArgumentException e) {
                throw new DaoException("Could not load treasury shift policies", e);
            }
        });
    }

    @Override
    public ShiftTrackingMode trackingMode(int treasuryId) throws DaoException {
        String sql = "SELECT tracking_mode FROM shift_treasury_policy WHERE treasury_id = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, treasuryId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? ShiftTrackingMode.valueOf(rs.getString(1)) : ShiftTrackingMode.NONE;
                }
            } catch (SQLException | IllegalArgumentException e) {
                throw new DaoException("Could not load the treasury shift policy", e);
            }
        });
    }

    @Override
    public boolean hasOpenShifts() throws DaoException {
        String sql = "SELECT EXISTS(SELECT 1 FROM user_shifts WHERE is_open = TRUE LIMIT 1)";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            } catch (SQLException e) {
                throw new DaoException("Could not check for open shifts", e);
            }
        });
    }

    @Override
    public void save(ShiftPolicy policy) throws DaoException {
        String sql = "UPDATE shift_policy SET mode=?, blind_close=?, auto_print_z=?, variance_tolerance=?, "
                + "require_variance_reason=?, require_supervisor_approval=?, "
                + "enforce_treasury_assignments=?, updated_at=CURRENT_TIMESTAMP WHERE id=1";
        update(sql, statement -> {
            statement.setString(1, policy.mode().name());
            statement.setBoolean(2, policy.blindClose());
            statement.setBoolean(3, policy.autoPrintZ());
            statement.setBigDecimal(4, policy.varianceTolerance());
            statement.setBoolean(5, policy.requireVarianceReason());
            statement.setBoolean(6, policy.requireSupervisorApproval());
            statement.setBoolean(7, policy.enforceTreasuryAssignments());
        });
    }

    @Override
    public void saveTreasury(TreasuryShiftPolicy policy) throws DaoException {
        String sql = "INSERT INTO shift_treasury_policy(treasury_id, tracking_mode) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE tracking_mode=VALUES(tracking_mode), updated_at=CURRENT_TIMESTAMP";
        update(sql, statement -> {
            statement.setInt(1, policy.treasuryId());
            statement.setString(2, policy.trackingMode().name());
        });
    }

    private <T> T withConnection(SqlWork<T> work) throws DaoException {
        java.sql.Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private void update(String sql, StatementBinder binder) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                if (statement.executeUpdate() != 1) throw new DaoException("Shift policy was not saved");
                return null;
            } catch (SQLException e) {
                throw new DaoException("Could not save the shift policy", e);
            }
        });
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(java.sql.Connection connection) throws DaoException; }

    @FunctionalInterface
    private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
}
