package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.UnitExtends;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SelPriceTypeModel extends UnitExtends {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();

    public SelPriceTypeModel(int id) {
        this.id = new SimpleIntegerProperty(id);
    }

    public SelPriceTypeModel(int id, String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
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
}