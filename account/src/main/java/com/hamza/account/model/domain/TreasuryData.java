package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.OperationType;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryData extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.DATE)
    private StringProperty date_inv = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.AMOUNT)
    private DoubleProperty amount = new SimpleDoubleProperty();
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

    public double getAmount() {
        return amount.get();
    }

    public void setAmount(double amount) {
        this.amount.set(amount);
    }

    public DoubleProperty amountProperty() {
        return amount;
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

    public String getDate_inv() {
        return date_inv.get();
    }

    public void setDate_inv(String date_inv) {
        this.date_inv.set(date_inv);
    }

    public StringProperty date_invProperty() {
        return date_inv;
    }
}
