package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.OperationType;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryData extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.DATE)
    private StringProperty date_inv = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.AMOUNT)
    private BigDecimal amount;
    @ColumnData(titleName = NamesTables.NOTES)
    private StringProperty notes = new SimpleStringProperty();
    private OperationType operationType;

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getNotes() {
        return notes.get();
    }

    public void setNotes(String notes) {
        this.notes.set(notes);
    }
}
