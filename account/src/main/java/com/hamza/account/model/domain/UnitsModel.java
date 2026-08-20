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

    private int unit_id;
    @ColumnData(titleName = NamesTables.NAME)
    private StringProperty unit_name = new SimpleStringProperty();
    /**
     * {@code units.value_d} - one number for the whole database, so it cannot
     * say that a carton of juice is 12 while a carton of cigarettes is 200. It
     * survives as the default the item screen offers, and as the fallback for a
     * unit row with no factor of its own; the factor that counts is per item, in
     * {@code items_units.quantity}.
     */
    @ColumnData(titleName = NamesTables.DEFAULT_FACTOR)
    private DoubleProperty value = new SimpleDoubleProperty();

    public UnitsModel(int unit_id) {
        this.unit_id = unit_id;
    }

    public UnitsModel(String unit_name) {
        this.unit_name = new SimpleStringProperty(unit_name);
    }

    public UnitsModel(int unit_id, String unit_name, double value) {
        this(unit_id);
        this.unit_name = new SimpleStringProperty(unit_name);
        this.value = new SimpleDoubleProperty(value);
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
