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
public class DelegateTarget extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    private int delegateId;

    @ColumnData(titleName = "المندوب")
    private final StringProperty delegateName = new SimpleStringProperty("");

    @ColumnData(titleName = "اسم الهدف")
    private final StringProperty targetName = new SimpleStringProperty("");

    @ColumnData(titleName = "نوع الهدف")
    private final StringProperty targetType = new SimpleStringProperty("");

    @ColumnData(titleName = "نوع الفترة")
    private final StringProperty periodType = new SimpleStringProperty("MONTHLY");

    @ColumnData(titleName = "من تاريخ")
    private final ObjectProperty<LocalDate> periodFrom = new SimpleObjectProperty<>();

    @ColumnData(titleName = "إلى تاريخ")
    private final ObjectProperty<LocalDate> periodTo = new SimpleObjectProperty<>();

    @ColumnData(titleName = "قيمة الهدف")
    private final DoubleProperty targetAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "كمية الهدف")
    private final DoubleProperty targetQuantity = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "عدد الهدف")
    private final IntegerProperty targetCount = new SimpleIntegerProperty(0);

    @ColumnData(titleName = "أقل نسبة ربح")
    private final DoubleProperty minProfitPercent = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "الحالة")
    private final StringProperty status = new SimpleStringProperty("ACTIVE");

    private final StringProperty notes = new SimpleStringProperty("");

    private int userId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getDelegateName() {
        return delegateName.get();
    }

    public void setDelegateName(String delegateName) {
        this.delegateName.set(delegateName);
    }

    public StringProperty delegateNameProperty() {
        return delegateName;
    }

    public String getTargetName() {
        return targetName.get();
    }

    public void setTargetName(String targetName) {
        this.targetName.set(targetName);
    }

    public StringProperty targetNameProperty() {
        return targetName;
    }

    public String getTargetType() {
        return targetType.get();
    }

    public void setTargetType(String targetType) {
        this.targetType.set(targetType);
    }

    public StringProperty targetTypeProperty() {
        return targetType;
    }

    public String getPeriodType() {
        return periodType.get();
    }

    public void setPeriodType(String periodType) {
        this.periodType.set(periodType);
    }

    public StringProperty periodTypeProperty() {
        return periodType;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom.get();
    }

    public void setPeriodFrom(LocalDate periodFrom) {
        this.periodFrom.set(periodFrom);
    }

    public ObjectProperty<LocalDate> periodFromProperty() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo.get();
    }

    public void setPeriodTo(LocalDate periodTo) {
        this.periodTo.set(periodTo);
    }

    public ObjectProperty<LocalDate> periodToProperty() {
        return periodTo;
    }

    public double getTargetAmount() {
        return targetAmount.get();
    }

    public void setTargetAmount(double targetAmount) {
        this.targetAmount.set(targetAmount);
    }

    public DoubleProperty targetAmountProperty() {
        return targetAmount;
    }

    public double getTargetQuantity() {
        return targetQuantity.get();
    }

    public void setTargetQuantity(double targetQuantity) {
        this.targetQuantity.set(targetQuantity);
    }

    public DoubleProperty targetQuantityProperty() {
        return targetQuantity;
    }

    public int getTargetCount() {
        return targetCount.get();
    }

    public void setTargetCount(int targetCount) {
        this.targetCount.set(targetCount);
    }

    public IntegerProperty targetCountProperty() {
        return targetCount;
    }

    public double getMinProfitPercent() {
        return minProfitPercent.get();
    }

    public void setMinProfitPercent(double minProfitPercent) {
        this.minProfitPercent.set(minProfitPercent);
    }

    public DoubleProperty minProfitPercentProperty() {
        return minProfitPercent;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
    }

    public String getNotes() {
        return notes.get();
    }

    public void setNotes(String notes) {
        this.notes.set(notes);
    }

    public StringProperty notesProperty() {
        return notes;
    }
}
