package com.hamza.account.model.domain;

import com.hamza.account.finance.MoneyMath;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Monetary summary used by live X and final Z shift reports. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSummary {
    @Builder.Default private BigDecimal openBalance = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalSales = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalSalesReturns = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalExpenses = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalDeposits = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalWithdrawals = BigDecimal.ZERO;
    @Builder.Default private BigDecimal otherIn = BigDecimal.ZERO;
    @Builder.Default private BigDecimal otherOut = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalIn = BigDecimal.ZERO;
    @Builder.Default private BigDecimal totalOut = BigDecimal.ZERO;
    private int invoicesCount;

    public BigDecimal getExpectedBalance() {
        return money(openBalance).add(money(totalIn)).subtract(money(totalOut));
    }

    public BigDecimal calculateDifference(BigDecimal actualCloseBalance) {
        return money(actualCloseBalance).subtract(getExpectedBalance());
    }

    public void setOpenBalance(BigDecimal value) { openBalance = money(value); }

    private static BigDecimal money(BigDecimal value) {
        return MoneyMath.money(value == null ? BigDecimal.ZERO : value);
    }
}
