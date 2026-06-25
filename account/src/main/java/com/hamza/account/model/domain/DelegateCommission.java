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
public class DelegateCommission extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private long id;

    private int delegateId;

    @ColumnData(titleName = "المندوب")
    private final StringProperty delegateName = new SimpleStringProperty("");

    @ColumnData(titleName = "تاريخ العمولة")
    private final ObjectProperty<LocalDate> commissionDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "نوع المرجع")
    private final StringProperty referenceType = new SimpleStringProperty("PERIOD");

    private long referenceId;

    @ColumnData(titleName = "قيمة المبيعات")
    private final DoubleProperty salesAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "قيمة الربح")
    private final DoubleProperty profitAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "نوع العمولة")
    private final StringProperty commissionType = new SimpleStringProperty("");

    @ColumnData(titleName = "نسبة / قيمة العمولة")
    private final DoubleProperty commissionRate = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "قيمة العمولة")
    private final DoubleProperty commissionAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "حالة الدفع")
    private final StringProperty paymentStatus = new SimpleStringProperty("UNPAID");

    @ColumnData(titleName = "المدفوع")
    private final DoubleProperty paidAmount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "تاريخ الدفع")
    private final ObjectProperty<LocalDate> paymentDate = new SimpleObjectProperty<>();

    private int treasuryId;

    @ColumnData(titleName = "الخزينة")
    private final StringProperty treasuryName = new SimpleStringProperty("");

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

    public LocalDate getCommissionDate() {
        return commissionDate.get();
    }

    public void setCommissionDate(LocalDate commissionDate) {
        this.commissionDate.set(commissionDate);
    }

    public ObjectProperty<LocalDate> commissionDateProperty() {
        return commissionDate;
    }

    public String getReferenceType() {
        return referenceType.get();
    }

    public void setReferenceType(String referenceType) {
        this.referenceType.set(referenceType);
    }

    public StringProperty referenceTypeProperty() {
        return referenceType;
    }

    public double getSalesAmount() {
        return salesAmount.get();
    }

    public void setSalesAmount(double salesAmount) {
        this.salesAmount.set(salesAmount);
    }

    public DoubleProperty salesAmountProperty() {
        return salesAmount;
    }

    public double getProfitAmount() {
        return profitAmount.get();
    }

    public void setProfitAmount(double profitAmount) {
        this.profitAmount.set(profitAmount);
    }

    public DoubleProperty profitAmountProperty() {
        return profitAmount;
    }

    public String getCommissionType() {
        return commissionType.get();
    }

    public void setCommissionType(String commissionType) {
        this.commissionType.set(commissionType);
    }

    public StringProperty commissionTypeProperty() {
        return commissionType;
    }

    public double getCommissionRate() {
        return commissionRate.get();
    }

    public void setCommissionRate(double commissionRate) {
        this.commissionRate.set(commissionRate);
    }

    public DoubleProperty commissionRateProperty() {
        return commissionRate;
    }

    public double getCommissionAmount() {
        return commissionAmount.get();
    }

    public void setCommissionAmount(double commissionAmount) {
        this.commissionAmount.set(commissionAmount);
    }

    public DoubleProperty commissionAmountProperty() {
        return commissionAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus.get();
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus.set(paymentStatus);
    }

    public StringProperty paymentStatusProperty() {
        return paymentStatus;
    }

    public double getPaidAmount() {
        return paidAmount.get();
    }

    public void setPaidAmount(double paidAmount) {
        this.paidAmount.set(paidAmount);
    }

    public DoubleProperty paidAmountProperty() {
        return paidAmount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate.get();
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate.set(paymentDate);
    }

    public ObjectProperty<LocalDate> paymentDateProperty() {
        return paymentDate;
    }

    public String getTreasuryName() {
        return treasuryName.get();
    }

    public void setTreasuryName(String treasuryName) {
        this.treasuryName.set(treasuryName);
    }

    public StringProperty treasuryNameProperty() {
        return treasuryName;
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
