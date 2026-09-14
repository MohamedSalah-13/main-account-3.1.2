package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The shift half of charging a shortage; the deduction itself belongs to the employee ledger. */
public interface ShiftShortageChargeRepository {
    ShiftShortageCase findForUpdate(int shiftId) throws DaoException;
    boolean activeEmployeeExists(int employeeId) throws DaoException;
    int appendCharge(int shiftId, int employeeId, int ledgerId, BigDecimal amount,
                     String reason, int actorUserId, LocalDateTime approvedAt) throws DaoException;
}
