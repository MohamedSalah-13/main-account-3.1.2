package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** JDBC transaction seam for the deduction and its immutable shift link. */
public final class JdbcShiftShortageChargeRepository extends AbstractDao<ShiftShortageCase>
        implements ShiftShortageChargeRepository {

    @Override
    public ShiftShortageCase findForUpdate(int shiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT s.id, s.user_id, s.is_open, s.difference,
                           EXISTS(SELECT 1 FROM shift_employee_shortage_charges c
                                  WHERE c.shift_id=s.id) already_charged
                    FROM user_shifts s WHERE s.id=? FOR UPDATE
                    """)) {
                statement.setInt(1, shiftId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? new ShiftShortageCase(rows.getInt("id"),
                            rows.getInt("user_id"), rows.getBoolean("is_open"),
                            rows.getBigDecimal("difference"), rows.getBoolean("already_charged")) : null;
                }
            }
        });
    }

    @Override
    public boolean activeEmployeeExists(int employeeId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM employees WHERE id=? AND is_active=TRUE)")) {
                statement.setInt(1, employeeId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() && rows.getBoolean(1);
                }
            }
        });
    }

    @Override
    public int appendCharge(int shiftId, int employeeId, int ledgerId, BigDecimal amount,
                            String reason, int actorUserId, LocalDateTime approvedAt)
            throws DaoException {
        return executeUpdate("""
                INSERT INTO shift_employee_shortage_charges
                    (shift_id, employee_id, employee_ledger_id, amount,
                     approval_reason, approved_by_user_id, approved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, shiftId, employeeId, ledgerId, amount, reason, actorUserId, approvedAt);
    }
}
