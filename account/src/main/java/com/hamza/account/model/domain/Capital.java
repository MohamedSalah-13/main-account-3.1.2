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
public class Capital extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;

    @ColumnData(titleName = "اسم رأس المال")
    private final StringProperty capitalName = new SimpleStringProperty();

    @ColumnData(titleName = "إجمالي رأس المال")
    private final DoubleProperty totalCapital = new SimpleDoubleProperty(0.0);

    @ColumnData(titleName = "تاريخ البداية")
    private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "تاريخ النهاية")
    private final ObjectProperty<LocalDate> endDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "نشط")
    private final BooleanProperty active = new SimpleBooleanProperty(true);

    private final StringProperty notes = new SimpleStringProperty();

    private int userId;

    // ===== capitalName =====
    public String getCapitalName() { return capitalName.get(); }
    public void setCapitalName(String capitalName) { this.capitalName.set(capitalName); }
    public StringProperty capitalNameProperty() { return capitalName; }

    // ===== totalCapital =====
    public double getTotalCapital() { return totalCapital.get(); }
    public void setTotalCapital(double totalCapital) { this.totalCapital.set(totalCapital); }
    public DoubleProperty totalCapitalProperty() { return totalCapital; }

    // ===== startDate =====
    public LocalDate getStartDate() { return startDate.get(); }
    public void setStartDate(LocalDate startDate) { this.startDate.set(startDate); }
    public ObjectProperty<LocalDate> startDateProperty() { return startDate; }

    // ===== endDate =====
    public LocalDate getEndDate() { return endDate.get(); }
    public void setEndDate(LocalDate endDate) { this.endDate.set(endDate); }
    public ObjectProperty<LocalDate> endDateProperty() { return endDate; }

    // ===== active =====
    public boolean isActive() { return active.get(); }
    public void setActive(boolean active) { this.active.set(active); }
    public BooleanProperty activeProperty() { return active; }

    // ===== notes =====
    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }
}
