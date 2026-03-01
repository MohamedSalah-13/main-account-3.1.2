package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ItemsUnitsModel extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    private int itemsId;
    @ColumnData(titleName = NamesTables.BARCODE)
    private StringProperty itemsBarcode = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.QUANTITY)
    private DoubleProperty quantityForUnit = new SimpleDoubleProperty();
    private ObjectProperty<UnitsModel> unitsModel = new SimpleObjectProperty<>();

    //TODO 10/12/2025 7:03 AM Mohamed: remove in other version
    private double buyPrice;
    private double selPrice;

    public String getItemsBarcode() {
        return itemsBarcode.get();
    }

    public void setItemsBarcode(String itemsBarcode) {
        this.itemsBarcode.set(itemsBarcode);
    }

    public StringProperty itemsBarcodeProperty() {
        return itemsBarcode;
    }

    public double getQuantityForUnit() {
        return quantityForUnit.get();
    }

    public void setQuantityForUnit(double quantityForUnit) {
        this.quantityForUnit.set(quantityForUnit);
    }

    public DoubleProperty quantityForUnitProperty() {
        return quantityForUnit;
    }

    public UnitsModel getUnitsModel() {
        return unitsModel.get();
    }

    public void setUnitsModel(UnitsModel unitsModel) {
        this.unitsModel.set(unitsModel);
    }

    public ObjectProperty<UnitsModel> unitsModelProperty() {
        return unitsModel;
    }
}
