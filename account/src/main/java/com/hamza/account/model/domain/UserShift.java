package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
public class UserShift extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    private int userId;

    @ColumnData(titleName = "اسم المستخدم")
    private final StringProperty username = new SimpleStringProperty();

    @ColumnData(titleName = "وقت الفتح")
    private final ObjectProperty<LocalDateTime> openTime = new SimpleObjectProperty<>();

    @ColumnData(titleName = "وقت الغلق")
    private final ObjectProperty<LocalDateTime> closeTime = new SimpleObjectProperty<>();

    @ColumnData(titleName = "الرصيد الافتتاحي")
    private final DoubleProperty openBalance = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الرصيد الختامي")
    private final DoubleProperty closeBalance = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الحالة")
    private final StringProperty status = new SimpleStringProperty();

    private final StringProperty notes = new SimpleStringProperty();

    private final BooleanProperty open = new SimpleBooleanProperty(false);

    // ===== حقول المرحلة 2: ملخص الوردية =====
    @ColumnData(titleName = "إجمالي المبيعات")
    private final DoubleProperty totalSales = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "مرتجعات المبيعات")
    private final DoubleProperty totalSalesReturns = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "المصروفات")
    private final DoubleProperty totalExpenses = new SimpleDoubleProperty(0.0);

    private final DoubleProperty totalDeposits = new SimpleDoubleProperty(0.0);
    private final DoubleProperty totalWithdrawals = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الرصيد المتوقع")
    private final DoubleProperty expectedBalance = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الفرق")
    private final DoubleProperty difference = new SimpleDoubleProperty(0.0);

    private final IntegerProperty invoicesCount = new SimpleIntegerProperty(0);

    public UserShift(int userId) {
        this.userId = userId;
    }

    // ===== username =====
    public String getUsername() { return username.get(); }
    public void setUsername(String username) { this.username.set(username); }
    public StringProperty usernameProperty() { return username; }

    // ===== openTime =====
    public LocalDateTime getOpenTime() { return openTime.get(); }
    public void setOpenTime(LocalDateTime openTime) { this.openTime.set(openTime); }
    public ObjectProperty<LocalDateTime> openTimeProperty() { return openTime; }

    // ===== closeTime =====
    public LocalDateTime getCloseTime() { return closeTime.get(); }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime.set(closeTime); }
    public ObjectProperty<LocalDateTime> closeTimeProperty() { return closeTime; }

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
    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }

    // ===== isOpen =====
    public boolean isOpen() { return open.get(); }
    public void setOpen(boolean value) { this.open.set(value); }
    public BooleanProperty openProperty() { return open; }

    // ===== totalSales =====
    public double getTotalSales() { return totalSales.get(); }
    public void setTotalSales(double v) { this.totalSales.set(v); }
    public DoubleProperty totalSalesProperty() { return totalSales; }

    // ===== totalSalesReturns =====
    public double getTotalSalesReturns() { return totalSalesReturns.get(); }
    public void setTotalSalesReturns(double v) { this.totalSalesReturns.set(v); }
    public DoubleProperty totalSalesReturnsProperty() { return totalSalesReturns; }

    // ===== totalExpenses =====
    public double getTotalExpenses() { return totalExpenses.get(); }
    public void setTotalExpenses(double v) { this.totalExpenses.set(v); }
    public DoubleProperty totalExpensesProperty() { return totalExpenses; }

    // ===== totalDeposits =====
    public double getTotalDeposits() { return totalDeposits.get(); }
    public void setTotalDeposits(double v) { this.totalDeposits.set(v); }
    public DoubleProperty totalDepositsProperty() { return totalDeposits; }

    // ===== totalWithdrawals =====
    public double getTotalWithdrawals() { return totalWithdrawals.get(); }
    public void setTotalWithdrawals(double v) { this.totalWithdrawals.set(v); }
    public DoubleProperty totalWithdrawalsProperty() { return totalWithdrawals; }

    // ===== expectedBalance =====
    public double getExpectedBalance() { return expectedBalance.get(); }
    public void setExpectedBalance(double v) { this.expectedBalance.set(v); }
    public DoubleProperty expectedBalanceProperty() { return expectedBalance; }

    // ===== difference =====
    public double getDifference() { return difference.get(); }
    public void setDifference(double v) { this.difference.set(v); }
    public DoubleProperty differenceProperty() { return difference; }

    // ===== invoicesCount =====
    public int getInvoicesCount() { return invoicesCount.get(); }
    public void setInvoicesCount(int v) { this.invoicesCount.set(v); }
    public IntegerProperty invoicesCountProperty() { return invoicesCount; }
}