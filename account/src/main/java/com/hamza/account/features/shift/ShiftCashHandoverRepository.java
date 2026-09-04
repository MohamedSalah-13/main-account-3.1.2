package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ShiftCashHandoverRepository {
    List<ShiftCashHandoverPolicy> loadPolicies() throws DaoException;
    void savePolicy(int sourceTreasuryId, int targetTreasuryId, BigDecimal retainedFloat,
                    boolean enabled, int actorUserId) throws DaoException;
    int appendForClosedShift(int shiftId, int sourceTreasuryId, BigDecimal actualBalance,
                             int handedByUserId, LocalDateTime requestedAt) throws DaoException;
    List<ShiftCashHandover> loadPending() throws DaoException;
    ShiftCashHandover findForUpdate(long handoverId) throws DaoException;
    int insertReceipt(long handoverId, int receivedByUserId, LocalDateTime receivedAt,
                      int treasuryTransferId, String note) throws DaoException;
    int appendVarianceAdjustment(int shiftId, int treasuryId, BigDecimal expectedBalance,
                                 BigDecimal actualBalance, BigDecimal differenceAmount,
                                 int cashMovementId, int actorUserId,
                                 LocalDateTime adjustedAt) throws DaoException;
    boolean hasBlockingPendingHandover(int treasuryId) throws DaoException;
    int insertOpenOverride(long handoverId, int approvedByUserId, String reason,
                           LocalDateTime approvedAt) throws DaoException;
}
