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
public class Partner extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    @ColumnData(titleName = "اسم الشريك")
    private final StringProperty partnerName = new SimpleStringProperty();

    @ColumnData(titleName = "كود الشريك")
    private final StringProperty partnerCode = new SimpleStringProperty();

    @ColumnData(titleName = "الرقم القومي")
    private final StringProperty nationalId = new SimpleStringProperty();

    @ColumnData(titleName = "الهاتف")
    private final StringProperty phone = new SimpleStringProperty();

    @ColumnData(titleName = "البريد الإلكتروني")
    private final StringProperty email = new SimpleStringProperty();

    @ColumnData(titleName = "العنوان")
    private final StringProperty address = new SimpleStringProperty();

    @ColumnData(titleName = "تاريخ الانضمام")
    private final ObjectProperty<LocalDate> joinDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "تاريخ الخروج")
    private final ObjectProperty<LocalDate> exitDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "نشط")
    private final BooleanProperty active = new SimpleBooleanProperty(true);

    private final StringProperty notes = new SimpleStringProperty();

    private int userId;

    // ===== partnerName =====
    public String getPartnerName() { return partnerName.get(); }
    public void setPartnerName(String partnerName) { this.partnerName.set(partnerName); }
    public StringProperty partnerNameProperty() { return partnerName; }

    // ===== partnerCode =====
    public String getPartnerCode() { return partnerCode.get(); }
    public void setPartnerCode(String partnerCode) { this.partnerCode.set(partnerCode); }
    public StringProperty partnerCodeProperty() { return partnerCode; }

    // ===== nationalId =====
    public String getNationalId() { return nationalId.get(); }
    public void setNationalId(String nationalId) { this.nationalId.set(nationalId); }
    public StringProperty nationalIdProperty() { return nationalId; }

    // ===== phone =====
    public String getPhone() { return phone.get(); }
    public void setPhone(String phone) { this.phone.set(phone); }
    public StringProperty phoneProperty() { return phone; }

    // ===== email =====
    public String getEmail() { return email.get(); }
    public void setEmail(String email) { this.email.set(email); }
    public StringProperty emailProperty() { return email; }

    // ===== address =====
    public String getAddress() { return address.get(); }
    public void setAddress(String address) { this.address.set(address); }
    public StringProperty addressProperty() { return address; }

    // ===== joinDate =====
    public LocalDate getJoinDate() { return joinDate.get(); }
    public void setJoinDate(LocalDate joinDate) { this.joinDate.set(joinDate); }
    public ObjectProperty<LocalDate> joinDateProperty() { return joinDate; }

    // ===== exitDate =====
    public LocalDate getExitDate() { return exitDate.get(); }
    public void setExitDate(LocalDate exitDate) { this.exitDate.set(exitDate); }
    public ObjectProperty<LocalDate> exitDateProperty() { return exitDate; }

    // ===== active =====
    public boolean isActive() { return active.get(); }
    public void setActive(boolean active) { this.active.set(active); }
    public BooleanProperty activeProperty() { return active; }

    // ===== notes =====
    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }
}
