package com.hamza.account.model.domain;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.features.shift.ShiftStatus;
import com.hamza.account.treasury.DefaultTreasury;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Plain, database-facing shift record. JavaFX binding belongs in the controller. */
@Getter
@Setter
@NoArgsConstructor
public class UserShift {
    private int id;
    private int userId;
    private int treasuryId = DefaultTreasury.ID;
    private String treasuryName;
    private String username;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private BigDecimal openBalance = BigDecimal.ZERO;
    private BigDecimal closeBalance = BigDecimal.ZERO;
    private ShiftStatus status = ShiftStatus.OPEN;
    private String notes;
    private boolean open;
    private BigDecimal totalSales = BigDecimal.ZERO;
    private BigDecimal totalSalesReturns = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal totalDeposits = BigDecimal.ZERO;
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;
    private BigDecimal totalCashIn = BigDecimal.ZERO;
    private BigDecimal totalCashOut = BigDecimal.ZERO;
    private BigDecimal expectedBalance = BigDecimal.ZERO;
    private BigDecimal difference = BigDecimal.ZERO;
    private int invoicesCount;

    public UserShift(int userId) { this.userId = userId; }

    public UserShift(int userId, int treasuryId) {
        this.userId = userId;
        this.treasuryId = treasuryId;
    }

    public void setOpenBalance(BigDecimal value) { openBalance = money(value); }
    public void setCloseBalance(BigDecimal value) { closeBalance = money(value); }
    public void setTotalSales(BigDecimal value) { totalSales = money(value); }
    public void setTotalSalesReturns(BigDecimal value) { totalSalesReturns = money(value); }
    public void setTotalExpenses(BigDecimal value) { totalExpenses = money(value); }
    public void setTotalDeposits(BigDecimal value) { totalDeposits = money(value); }
    public void setTotalWithdrawals(BigDecimal value) { totalWithdrawals = money(value); }
    public void setTotalCashIn(BigDecimal value) { totalCashIn = money(value); }
    public void setTotalCashOut(BigDecimal value) { totalCashOut = money(value); }
    public void setExpectedBalance(BigDecimal value) { expectedBalance = money(value); }
    public void setDifference(BigDecimal value) { difference = money(value); }

    private static BigDecimal money(BigDecimal value) {
        return MoneyMath.money(value == null ? BigDecimal.ZERO : value);
    }
}
