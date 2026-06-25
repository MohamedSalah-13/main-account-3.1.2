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
public class DelegateProfile extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int profileId;

    @ColumnData(titleName = "كود المندوب")
    private final IntegerProperty delegateId = new SimpleIntegerProperty();

    @ColumnData(titleName = "اسم المندوب")
    private final StringProperty delegateName = new SimpleStringProperty("");

    @ColumnData(titleName = "التليفون")
    private final StringProperty tel = new SimpleStringProperty("");

    @ColumnData(titleName = "البريد الإلكتروني")
    private final StringProperty email = new SimpleStringProperty("");

    @ColumnData(titleName = "العنوان")
    private final StringProperty address = new SimpleStringProperty("");

    @ColumnData(titleName = "المرتب")
    private final DoubleProperty salary = new SimpleDoubleProperty(0);

    private int areaId;

    @ColumnData(titleName = "المنطقة")
    private final StringProperty areaName = new SimpleStringProperty("");

    private int supervisorId;

    @ColumnData(titleName = "المشرف")
    private final StringProperty supervisorName = new SimpleStringProperty("");

    @ColumnData(titleName = "نوع العمولة")
    private final StringProperty commissionType = new SimpleStringProperty("NONE");

    @ColumnData(titleName = "قيمة العمولة")
    private final DoubleProperty commissionValue = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "هدف التحصيل")
    private final DoubleProperty collectionTarget = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "حد الائتمان")
    private final DoubleProperty creditLimit = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "نشط")
    private final BooleanProperty active = new SimpleBooleanProperty(true);

    private final StringProperty notes = new SimpleStringProperty("");

    private int userId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int getDelegateId() {
        return delegateId.get();
    }

    public void setDelegateId(int delegateId) {
        this.delegateId.set(delegateId);
    }

    public IntegerProperty delegateIdProperty() {
        return delegateId;
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

    public String getTel() {
        return tel.get();
    }

    public void setTel(String tel) {
        this.tel.set(tel);
    }

    public StringProperty telProperty() {
        return tel;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    public StringProperty emailProperty() {
        return email;
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public StringProperty addressProperty() {
        return address;
    }

    public double getSalary() {
        return salary.get();
    }

    public void setSalary(double salary) {
        this.salary.set(salary);
    }

    public DoubleProperty salaryProperty() {
        return salary;
    }

    public String getAreaName() {
        return areaName.get();
    }

    public void setAreaName(String areaName) {
        this.areaName.set(areaName);
    }

    public StringProperty areaNameProperty() {
        return areaName;
    }

    public String getSupervisorName() {
        return supervisorName.get();
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName.set(supervisorName);
    }

    public StringProperty supervisorNameProperty() {
        return supervisorName;
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

    public double getCommissionValue() {
        return commissionValue.get();
    }

    public void setCommissionValue(double commissionValue) {
        this.commissionValue.set(commissionValue);
    }

    public DoubleProperty commissionValueProperty() {
        return commissionValue;
    }

    public double getCollectionTarget() {
        return collectionTarget.get();
    }

    public void setCollectionTarget(double collectionTarget) {
        this.collectionTarget.set(collectionTarget);
    }

    public DoubleProperty collectionTargetProperty() {
        return collectionTarget;
    }

    public double getCreditLimit() {
        return creditLimit.get();
    }

    public void setCreditLimit(double creditLimit) {
        this.creditLimit.set(creditLimit);
    }

    public DoubleProperty creditLimitProperty() {
        return creditLimit;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
    }

    public BooleanProperty activeProperty() {
        return active;
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
