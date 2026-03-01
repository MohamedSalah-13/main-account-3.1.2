package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.table.ColumnData;
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
public class Expenses {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();

    public Expenses(int id) {
        this.id = id;
    }

    public Expenses(int id, String name) {
        this(id);
        this.name = new SimpleStringProperty(name);
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
