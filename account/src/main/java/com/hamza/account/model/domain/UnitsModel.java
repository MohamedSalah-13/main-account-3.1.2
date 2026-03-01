package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class UnitsModel extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty unit_id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty unit_name = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.QUANTITY)
    private DoubleProperty value = new SimpleDoubleProperty();

    public UnitsModel(int unit_id) {
        this.unit_id = new SimpleIntegerProperty(unit_id);
    }

    public UnitsModel(String unit_name) {
        this.unit_name = new SimpleStringProperty(unit_name);
    }

    public UnitsModel(int unit_id, String unit_name, double value) {
        this(unit_id);
        this.unit_name = new SimpleStringProperty(unit_name);
        this.value = new SimpleDoubleProperty(value);
    }

    public int getUnit_id() {
        return unit_id.get();
    }

    public void setUnit_id(int unit_id) {
        this.unit_id.set(unit_id);
    }


    public IntegerProperty unit_idProperty() {
        return unit_id;
    }


    public String getUnit_name() {
        return unit_name.get();
    }

    public void setUnit_name(String unit_name) {
        this.unit_name.set(unit_name);
    }


    public StringProperty unit_nameProperty() {
        return unit_name;
    }

    public double getValue() {
        return value.get();
    }

    public void setValue(double value) {
        this.value.set(value);
    }

    public DoubleProperty valueProperty() {
        return value;
    }
}
