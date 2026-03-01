package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
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
public class TreasuryModel extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.FIRST_BALANCE)
    private double firstBalance;

    public TreasuryModel(int id) {
        this.id = id;
    }

    public TreasuryModel(int id, String name, double firstBalance) {
        this(id);
        this.name = new SimpleStringProperty(name);
        this.firstBalance = firstBalance;
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
