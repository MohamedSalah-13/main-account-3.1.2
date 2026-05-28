package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
public class ProfitLossDistribution extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    private int capitalId;

    @ColumnData(titleName = "رأس المال")
    private final StringProperty capitalName = new SimpleStringProperty();

    @ColumnData(titleName = "تاريخ التوزيع")
    private final ObjectProperty<LocalDate> distributionDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "من تاريخ")
    private final ObjectProperty<LocalDate> periodFrom = new SimpleObjectProperty<>();

    @ColumnData(titleName = "إلى تاريخ")
    private final ObjectProperty<LocalDate> periodTo = new SimpleObjectProperty<>();

    @ColumnData(titleName = "إجمالي الإيرادات")
    private final DoubleProperty totalRevenue = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "إجمالي المصروفات")
    private final DoubleProperty totalExpenses = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "صافي الربح/الخسارة")
    private final DoubleProperty netProfitLoss = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "النوع")
    private final BooleanProperty isProfit = new SimpleBooleanProperty(true);

    @ColumnData(titleName = "حالة التوزيع")
    private final StringProperty distributionStatus = new SimpleStringProperty("PENDING");

    private final ObjectProperty<LocalDateTime> distributedAt = new SimpleObjectProperty<>();

    private final StringProperty notes = new SimpleStringProperty();

    private int userId;

    // ===== capitalName =====
    public String getCapitalName() { return capitalName.get(); }
    public void setCapitalName(String capitalName) { this.capitalName.set(capitalName); }
    public StringProperty capitalNameProperty() { return capitalName; }

    // ===== distributionDate =====
    public LocalDate getDistributionDate() { return distributionDate.get(); }
    public void setDistributionDate(LocalDate distributionDate) { this.distributionDate.set(distributionDate); }
    public ObjectProperty<LocalDate> distributionDateProperty() { return distributionDate; }

    // ===== periodFrom =====
    public LocalDate getPeriodFrom() { return periodFrom.get(); }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom.set(periodFrom); }
    public ObjectProperty<LocalDate> periodFromProperty() { return periodFrom; }

    // ===== periodTo =====
    public LocalDate getPeriodTo() { return periodTo.get(); }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo.set(periodTo); }
    public ObjectProperty<LocalDate> periodToProperty() { return periodTo; }

    // ===== totalRevenue =====
    public double getTotalRevenue() { return totalRevenue.get(); }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue.set(totalRevenue); }
    public DoubleProperty totalRevenueProperty() { return totalRevenue; }

    // ===== totalExpenses =====
    public double getTotalExpenses() { return totalExpenses.get(); }
    public void setTotalExpenses(double totalExpenses) { this.totalExpenses.set(totalExpenses); }
    public DoubleProperty totalExpensesProperty() { return totalExpenses; }

    // ===== netProfitLoss =====
    public double getNetProfitLoss() { return netProfitLoss.get(); }
    public void setNetProfitLoss(double netProfitLoss) { this.netProfitLoss.set(netProfitLoss); }
    public DoubleProperty netProfitLossProperty() { return netProfitLoss; }

    // ===== isProfit =====
    public boolean isIsProfit() { return isProfit.get(); }
    public void setIsProfit(boolean isProfit) { this.isProfit.set(isProfit); }
    public BooleanProperty isProfitProperty() { return isProfit; }

    // ===== distributionStatus =====
    public String getDistributionStatus() { return distributionStatus.get(); }
    public void setDistributionStatus(String distributionStatus) { this.distributionStatus.set(distributionStatus); }
    public StringProperty distributionStatusProperty() { return distributionStatus; }

    // ===== distributedAt =====
    public LocalDateTime getDistributedAt() { return distributedAt.get(); }
    public void setDistributedAt(LocalDateTime distributedAt) { this.distributedAt.set(distributedAt); }
    public ObjectProperty<LocalDateTime> distributedAtProperty() { return distributedAt; }

    // ===== notes =====
    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }
}
