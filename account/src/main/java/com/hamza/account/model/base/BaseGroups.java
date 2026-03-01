package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
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
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseGroups extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();

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
