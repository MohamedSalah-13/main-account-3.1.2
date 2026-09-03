package com.hamza.account.features.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable pending two-person close request shown to supervisors. */
public record ShiftCloseRequest(
        long id,
        int shiftId,
        int shiftUserId,
        String shiftUsername,
        int treasuryId,
        String treasuryName,
        int requestedByUserId,
        String requestedByUsername,
        LocalDateTime requestedAt,
        BigDecimal actualBalance,
        BigDecimal expectedBalance,
        BigDecimal difference,
        BigDecimal totalSales,
        BigDecimal totalSalesReturns,
        BigDecimal totalExpenses,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        int invoicesCount,
        long ledgerLastId,
        String reason) {
}
