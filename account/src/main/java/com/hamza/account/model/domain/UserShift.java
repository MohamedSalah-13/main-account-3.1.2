package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserShift extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    private int userId;

    @ColumnData(titleName = "اسم المستخدم")
    private StringProperty username = new SimpleStringProperty();

    @ColumnData(titleName = "وقت الفتح")
    private ObjectProperty<LocalDateTime> openTime = new SimpleObjectProperty<>();

    @ColumnData(titleName = "وقت الغلق")
    private ObjectProperty<LocalDateTime> closeTime = new SimpleObjectProperty<>();

    @ColumnData(titleName = "الرصيد الافتتاحي")
    private DoubleProperty openBalance = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الرصيد الختامي")
    private DoubleProperty closeBalance = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "الحالة")
    private StringProperty status = new SimpleStringProperty();

    private StringProperty notes = new SimpleStringProperty();

    private boolean isOpen;

    public UserShift(int userId) {
        this.userId = userId;
    }

    // Property getters
    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public LocalDateTime getOpenTime() {
        return openTime.get();
    }

    public void setOpenTime(LocalDateTime openTime) {
        this.openTime.set(openTime);
    }

    public ObjectProperty<LocalDateTime> openTimeProperty() {
        return openTime;
    }

    public LocalDateTime getCloseTime() {
        return closeTime.get();
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime.set(closeTime);
    }

    public ObjectProperty<LocalDateTime> closeTimeProperty() {
        return closeTime;
    }

    public double getOpenBalance() {
        return openBalance.get();
    }

    public void setOpenBalance(double openBalance) {
        this.openBalance.set(openBalance);
    }

    public DoubleProperty openBalanceProperty() {
        return openBalance;
    }

    public double getCloseBalance() {
        return closeBalance.get();
    }

    public void setCloseBalance(double closeBalance) {
        this.closeBalance.set(closeBalance);
    }

    public DoubleProperty closeBalanceProperty() {
        return closeBalance;
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
