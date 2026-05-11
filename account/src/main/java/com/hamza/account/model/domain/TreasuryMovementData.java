package com.hamza.account.model.domain;

import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.TreasuryMovementType;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TreasuryMovementData extends DForColumnTable {

    private LongProperty id = new SimpleLongProperty();

    private StringProperty movementDate = new SimpleStringProperty();

    private StringProperty treasuryName = new SimpleStringProperty();

    private StringProperty movementTypeName = new SimpleStringProperty();

    private BigDecimal amountIn = BigDecimal.ZERO;

    private BigDecimal amountOut = BigDecimal.ZERO;

    private BigDecimal balanceAfter = BigDecimal.ZERO;

    private StringProperty notes = new SimpleStringProperty();

    private TreasuryMovementType movementType;

    public long getId() {
        return id.get();
    }

    public void setId(long id) {
        this.id.set(id);
    }

    public LongProperty idProperty() {
        return id;
    }

    public String getMovementDate() {
        return movementDate.get();
    }

    public void setMovementDate(String movementDate) {
        this.movementDate.set(movementDate);
    }

    public StringProperty movementDateProperty() {
        return movementDate;
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

    public String getMovementTypeName() {
        return movementTypeName.get();
    }

    public void setMovementTypeName(String movementTypeName) {
        this.movementTypeName.set(movementTypeName);
    }

    public StringProperty movementTypeNameProperty() {
        return movementTypeName;
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