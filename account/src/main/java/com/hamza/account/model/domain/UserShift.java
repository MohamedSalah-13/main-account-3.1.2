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
}