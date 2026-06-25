package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class DelegateTargetReport extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int targetId;

    @ColumnData(titleName = "اسم الهدف")
    private final StringProperty targetName = new SimpleStringProperty("");

    @ColumnData(titleName = "نوع الهدف")
    private final StringProperty targetType = new SimpleStringProperty("");

    @ColumnData(titleName = "نوع الفترة")
    private final StringProperty periodType = new SimpleStringProperty("");

    @ColumnData(titleName = "من تاريخ")
    private final ObjectProperty<LocalDate> periodFrom = new SimpleObjectProperty<>();

    @ColumnData(titleName = "إلى تاريخ")
    private final ObjectProperty<LocalDate> periodTo = new SimpleObjectProperty<>();

    private int delegateId;

    @ColumnData(titleName = "المندوب")
    private final StringProperty delegateName = new SimpleStringProperty("");

    @ColumnData(titleName = "قيمة الهدف")
    private final DoubleProperty targetAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "كمية الهدف")
    private final DoubleProperty targetQuantity = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "عدد الهدف")
    private final IntegerProperty targetCount = new SimpleIntegerProperty(0);

    @ColumnData(titleName = "المحقق")
    private final DoubleProperty achievedValue = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "المطلوب")
    private final DoubleProperty requiredValue = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "نسبة التحقيق")
    private final DoubleProperty achievementPercent = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "المتبقي")
    private final DoubleProperty remainingValue = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "حالة التحقيق")
    private final StringProperty achievementStatus = new SimpleStringProperty("");

    @ColumnData(titleName = "حالة الهدف")
    private final StringProperty targetStatus = new SimpleStringProperty("");

    private final StringProperty notes = new SimpleStringProperty("");

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

    public String getDelegateName() {
        return delegateName.get();
    }

    public void setDelegateName(String delegateName) {
        this.delegateName.set(delegateName);
    }

    public StringProperty delegateNameProperty() {
        return delegateName;
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

    public double getAchievedValue() {
        return achievedValue.get();
    }

    public void setAchievedValue(double achievedValue) {
        this.achievedValue.set(achievedValue);
    }

    public DoubleProperty achievedValueProperty() {
        return achievedValue;
    }

    public double getRequiredValue() {
        return requiredValue.get();
    }

    public void setRequiredValue(double requiredValue) {
        this.requiredValue.set(requiredValue);
    }

    public DoubleProperty requiredValueProperty() {
        return requiredValue;
    }

    public double getAchievementPercent() {
        return achievementPercent.get();
    }

    public void setAchievementPercent(double achievementPercent) {
        this.achievementPercent.set(achievementPercent);
    }

    public DoubleProperty achievementPercentProperty() {
        return achievementPercent;
    }

    public double getRemainingValue() {
        return remainingValue.get();
    }

    public void setRemainingValue(double remainingValue) {
        this.remainingValue.set(remainingValue);
    }

    public DoubleProperty remainingValueProperty() {
        return remainingValue;
    }

    public String getAchievementStatus() {
        return achievementStatus.get();
    }

    public void setAchievementStatus(String achievementStatus) {
        this.achievementStatus.set(achievementStatus);
    }

    public StringProperty achievementStatusProperty() {
        return achievementStatus;
    }

    public String getTargetStatus() {
        return targetStatus.get();
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus.set(targetStatus);
    }

    public StringProperty targetStatusProperty() {
        return targetStatus;
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
