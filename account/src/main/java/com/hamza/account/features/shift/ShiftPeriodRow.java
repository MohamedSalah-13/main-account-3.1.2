package com.hamza.account.features.shift;

import java.math.BigDecimal;

/** One cashier/till total built from immutable shift-close snapshots. */
public record ShiftPeriodRow(
        int userId,
        String username,
        int treasuryId,
        String treasuryName,
        long shiftCount,
        long openShiftCount,
        long closedShiftCount,
        BigDecimal totalSales,
        BigDecimal totalSalesReturns,
        BigDecimal totalExpenses,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal totalExpectedBalance,
        BigDecimal totalActualBalance,
        BigDecimal totalDifference,
        long invoicesCount) {

    public ShiftPeriodRow {
        username = safe(username);
        treasuryName = safe(treasuryName);
        totalSales = money(totalSales);
        totalSalesReturns = money(totalSalesReturns);
        totalExpenses = money(totalExpenses);
        totalDeposits = money(totalDeposits);
        totalWithdrawals = money(totalWithdrawals);
        totalExpectedBalance = money(totalExpectedBalance);
        totalActualBalance = money(totalActualBalance);
        totalDifference = money(totalDifference);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
