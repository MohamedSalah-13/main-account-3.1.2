package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Area;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public abstract class BaseNames extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty name = new SimpleStringProperty();
    private String tel;
    private String address;
    private String notes;
    private double first_balance;
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
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getFirst_balance() {
        return first_balance;
    }

    public void setFirst_balance(double first_balance) {
        this.first_balance = first_balance;
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
