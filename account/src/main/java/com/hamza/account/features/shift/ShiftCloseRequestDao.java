package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence boundary for immutable close requests and decisions. */
public final class ShiftCloseRequestDao extends AbstractDao<ShiftCloseRequest> {
    private static final String SELECT_PENDING = """
            SELECT r.*, us.user_id AS shift_user_id, us.treasury_id,
                   owner.user_name AS shift_username, requester.user_name AS requester_username,
                   t.t_name AS treasury_name
            FROM shift_close_requests r
            JOIN user_shifts us ON us.id=r.shift_id
            JOIN users owner ON owner.id=us.user_id
            JOIN users requester ON requester.id=r.requested_by_user_id
            JOIN treasury t ON t.id=us.treasury_id
            LEFT JOIN shift_close_decisions d ON d.request_id=r.id
            WHERE d.id IS NULL
            """;

    public long append(UserShift shift, ShiftSummary summary, java.math.BigDecimal actualBalance,
                       String reason, int requesterId, LocalDateTime requestedAt) throws DaoException {
        String sql = """
                INSERT INTO shift_close_requests (
                    shift_id, requested_by_user_id, requested_at, actual_balance,
                    expected_balance, difference_amount, total_sales, total_sales_returns,
                    total_expenses, total_deposits, total_withdrawals, total_cash_in,
                    total_cash_out, invoices_count, ledger_last_id, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    COALESCE((SELECT MAX(id) FROM shift_cash_ledger WHERE shift_id=?),0), ?)
                """;
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                statement.setInt(index++, shift.getId());
                statement.setInt(index++, requesterId);
                statement.setTimestamp(index++, Timestamp.valueOf(requestedAt));
                statement.setBigDecimal(index++, actualBalance);
                statement.setBigDecimal(index++, summary.getExpectedBalance());
                statement.setBigDecimal(index++, summary.calculateDifference(actualBalance));
                statement.setBigDecimal(index++, summary.getTotalSales());
                statement.setBigDecimal(index++, summary.getTotalSalesReturns());
                statement.setBigDecimal(index++, summary.getTotalExpenses());
                statement.setBigDecimal(index++, summary.getTotalDeposits());
                statement.setBigDecimal(index++, summary.getTotalWithdrawals());
                statement.setBigDecimal(index++, summary.getTotalIn());
                statement.setBigDecimal(index++, summary.getTotalOut());
                statement.setInt(index++, summary.getInvoicesCount());
                statement.setInt(index++, shift.getId());
                statement.setString(index, reason.trim());
                if (statement.executeUpdate() != 1) throw new DaoException("Close request was not appended");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
                throw new DaoException("Close request id was not generated");
            } catch (SQLException e) {
                throw new DaoException("Could not append close request", e);
            }
        });
    }

    public List<ShiftCloseRequest> loadPending() throws DaoException {
        return queryForObjects(SELECT_PENDING + " ORDER BY r.requested_at, r.id", this::map);
    }

    public ShiftCloseRequest pendingForShift(int shiftId, boolean lock) throws DaoException {
        return queryForObject(SELECT_PENDING + " AND r.shift_id=? ORDER BY r.id DESC LIMIT 1"
                + (lock ? " FOR UPDATE" : ""), this::map, shiftId);
    }

    public int decide(long requestId, int actorId, String decision, String note,
                      LocalDateTime decidedAt) throws DaoException {
        return executeUpdate("""
                INSERT INTO shift_close_decisions
                    (request_id, decided_by_user_id, decision_type, decision_note, decided_at)
                VALUES (?, ?, ?, ?, ?)
                """, requestId, actorId, decision, normalize(note), Timestamp.valueOf(decidedAt));
    }

    public long currentLedgerLastId(int shiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COALESCE(MAX(id),0) FROM shift_cash_ledger WHERE shift_id=?")) {
                statement.setInt(1, shiftId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read the shift journal watermark", e);
            }
        });
    }

    @Override
    public ShiftCloseRequest map(ResultSet result) throws DaoException {
        try {
            return new ShiftCloseRequest(
                    result.getLong("id"), result.getInt("shift_id"), result.getInt("shift_user_id"),
                    result.getString("shift_username"), result.getInt("treasury_id"),
                    result.getString("treasury_name"), result.getInt("requested_by_user_id"),
                    result.getString("requester_username"), result.getTimestamp("requested_at").toLocalDateTime(),
                    result.getBigDecimal("actual_balance"), result.getBigDecimal("expected_balance"),
                    result.getBigDecimal("difference_amount"), result.getBigDecimal("total_sales"),
                    result.getBigDecimal("total_sales_returns"), result.getBigDecimal("total_expenses"),
                    result.getBigDecimal("total_deposits"), result.getBigDecimal("total_withdrawals"),
                    result.getBigDecimal("total_cash_in"), result.getBigDecimal("total_cash_out"),
                    result.getInt("invoices_count"), result.getLong("ledger_last_id"), result.getString("reason"));
        } catch (SQLException e) {
            throw new DaoException("Could not map a shift close request", e);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override public List<ShiftCloseRequest> loadAll() throws DaoException { return loadPending(); }
    @Override public int insert(ShiftCloseRequest value) { throw new UnsupportedOperationException(); }
    @Override public int update(ShiftCloseRequest value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public ShiftCloseRequest getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(ShiftCloseRequest value) { throw new UnsupportedOperationException(); }
}
