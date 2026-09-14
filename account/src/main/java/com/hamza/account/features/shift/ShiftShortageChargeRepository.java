package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ShiftShortageChargeRepository {
    ShiftShortageCase findForUpdate(int shiftId) throws DaoException;
    boolean activeEmployeeExists(int employeeId) throws DaoException;
    int insertDeduction(int employeeId, LocalDate date, BigDecimal amount,
                        String notes, int actorUserId) throws DaoException;
    int appendCharge(int shiftId, int employeeId, int ledgerId, BigDecimal amount,
                     String reason, int actorUserId, LocalDateTime approvedAt) throws DaoException;
}
