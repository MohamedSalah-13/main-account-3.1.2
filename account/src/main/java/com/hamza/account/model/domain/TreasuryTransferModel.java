package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseEntity;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryTransferModel extends BaseEntity {

    @ColumnData(titleName = NamesTables.AMOUNT)
    private DoubleProperty amount = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate date;
    @ColumnData(titleName = "من خزينة")
    private String treasuryNameFrom;
    @ColumnData(titleName = "إلى خزينة")
    private String treasuryNameTo;
    @ColumnData(titleName = NamesTables.NOTES)
    private String notes;

    private ObjectProperty<TreasuryModel> treasuryFrom = new SimpleObjectProperty<>();
    private ObjectProperty<TreasuryModel> treasuryTo = new SimpleObjectProperty<>();

    public TreasuryTransferModel(int id, double amount, LocalDate date, String notes, int treasuryFrom, int treasuryTo) {
        this.setId(id);
        this.amount = new SimpleDoubleProperty(amount);
        this.date = date;
        this.notes = notes;
        this.treasuryFrom = new SimpleObjectProperty<>(new TreasuryModel(treasuryFrom));
        this.treasuryTo = new SimpleObjectProperty<>(new TreasuryModel(treasuryTo));
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

    public TreasuryModel getTreasuryFrom() {
        return treasuryFrom.get();
    }

    public void setTreasuryFrom(TreasuryModel treasuryFrom) {
        this.treasuryFrom.set(treasuryFrom);
    }

    public ObjectProperty<TreasuryModel> treasuryFromProperty() {
        return treasuryFrom;
    }

    public TreasuryModel getTreasuryTo() {
        return treasuryTo.get();
    }

    public void setTreasuryTo(TreasuryModel treasuryTo) {
        this.treasuryTo.set(treasuryTo);
    }

    public ObjectProperty<TreasuryModel> treasuryToProperty() {
        return treasuryTo;
    }

}
