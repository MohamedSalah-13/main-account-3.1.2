package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Area;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public abstract class BaseNames extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.TEL)
    private StringProperty tel = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.ADDRESS)
    private StringProperty address = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.NOTES)
    private StringProperty notes = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.FIRST_BALANCE)
    private DoubleProperty first_balance = new SimpleDoubleProperty();
    private ObjectProperty<Area> area = new SimpleObjectProperty<>();


    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
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

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public StringProperty addressProperty() {
        return address;
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

    public double getFirst_balance() {
        return first_balance.get();
    }

    public void setFirst_balance(double first_balance) {
        this.first_balance.set(first_balance);
    }

    public DoubleProperty first_balanceProperty() {
        return first_balance;
    }

    public Area getArea() {
        return area.get();
    }

    public void setArea(Area area) {
        this.area.set(area);
    }

    public ObjectProperty<Area> areaProperty() {
        return area;
    }
}
