package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ShiftVarianceSettlementRepository {
    int append(int shiftId, int treasuryId, BigDecimal expected, BigDecimal actual,
               BigDecimal difference, LocalDate originalShiftDate, int actorUserId,
               LocalDateTime requestedAt) throws DaoException;
    List<ShiftVarianceSettlement> loadPending() throws DaoException;
    ShiftVarianceSettlement findPendingForUpdate(long requestId) throws DaoException;
    boolean isPendingForShift(int shiftId) throws DaoException;
}
