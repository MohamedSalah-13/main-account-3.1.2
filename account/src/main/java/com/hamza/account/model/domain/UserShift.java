package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
public class UserShift extends DForColumnTable {

    private int id;

    private int userId;

    /**
     * The till this shift was opened on. The summary is filtered by it - a shift on
     * the drawer must not be charged with what the e-wallet collected (V22).
     */
    private int treasuryId = com.hamza.account.treasury.DefaultTreasury.ID;

    private final StringProperty treasuryName = new SimpleStringProperty();

    private final StringProperty username = new SimpleStringProperty();

    private LocalDateTime openTime;

    private LocalDateTime closeTime;

    private final DoubleProperty openBalance = new SimpleDoubleProperty(0.0);

    private final DoubleProperty closeBalance = new SimpleDoubleProperty(0.0);

    private final StringProperty status = new SimpleStringProperty();

    private String notes;

    private boolean open;

    // ===== حقول المرحلة 2: ملخص الوردية =====
    private double totalSales;

    private double totalSalesReturns;

    private double totalExpenses;

    private double totalDeposits;
    private double totalWithdrawals;

    /** Everything that entered and left the till during the shift, all headings. */
    private double totalCashIn;

    private double totalCashOut;

    private double expectedBalance;

    private double difference;

    private int invoicesCount;

    public UserShift(int userId) {
        this.userId = userId;
    }

    public UserShift(int userId, int treasuryId) {
        this.userId = userId;
        this.treasuryId = treasuryId;
    }

    // ===== treasuryName =====
    public String getTreasuryName() { return treasuryName.get(); }
    public void setTreasuryName(String name) { this.treasuryName.set(name); }
    public StringProperty treasuryNameProperty() { return treasuryName; }

    // ===== username =====
    public String getUsername() { return username.get(); }
    public void setUsername(String username) { this.username.set(username); }
    public StringProperty usernameProperty() { return username; }

    // ===== openTime =====
    public LocalDateTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalDateTime openTime) { this.openTime = openTime; }

    // ===== closeTime =====
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }

    // ===== openBalance =====
    public double getOpenBalance() { return openBalance.get(); }
    public void setOpenBalance(double openBalance) { this.openBalance.set(openBalance); }
    public DoubleProperty openBalanceProperty() { return openBalance; }

    // ===== closeBalance =====
    public double getCloseBalance() { return closeBalance.get(); }
    public void setCloseBalance(double closeBalance) { this.closeBalance.set(closeBalance); }
    public DoubleProperty closeBalanceProperty() { return closeBalance; }

    // ===== status =====
    public String getStatus() { return status.get(); }
    public void setStatus(String status) { this.status.set(status); }
    public StringProperty statusProperty() { return status; }

    // ===== notes =====
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // ===== isOpen =====
    public boolean isOpen() { return open; }
    public void setOpen(boolean value) { this.open = value; }

    // ===== totalSales =====
    public double getTotalSales() { return totalSales; }
    public void setTotalSales(double v) { this.totalSales = v; }

    // ===== totalSalesReturns =====
    public double getTotalSalesReturns() { return totalSalesReturns; }
    public void setTotalSalesReturns(double v) { this.totalSalesReturns = v; }

    // ===== totalExpenses =====
    public double getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(double v) { this.totalExpenses = v; }

    // ===== totalDeposits =====
    public double getTotalDeposits() { return totalDeposits; }
    public void setTotalDeposits(double v) { this.totalDeposits = v; }

    // ===== totalWithdrawals =====
    public double getTotalWithdrawals() { return totalWithdrawals; }
    public void setTotalWithdrawals(double v) { this.totalWithdrawals = v; }

    // ===== expectedBalance =====
    public double getExpectedBalance() { return expectedBalance; }
    public void setExpectedBalance(double v) { this.expectedBalance = v; }

    // ===== difference =====
    public double getDifference() { return difference; }
    public void setDifference(double v) { this.difference = v; }

    // ===== invoicesCount =====
    public int getInvoicesCount() { return invoicesCount; }
    public void setInvoicesCount(int v) { this.invoicesCount = v; }
}