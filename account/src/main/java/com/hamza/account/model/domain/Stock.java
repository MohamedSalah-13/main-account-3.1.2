package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Stock extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty name = new SimpleStringProperty();
    private String address;

    public Stock(int id) {
        this.id = new SimpleIntegerProperty(id);
    }

    public Stock(String name) {
        this.name = new SimpleStringProperty(name);
    }

    public Stock(int id, String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
    }

    public Stock(int id, String name, String address) {
        this(id, name);
        this.address = address;
    }

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

}
