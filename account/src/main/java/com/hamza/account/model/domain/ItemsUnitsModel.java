package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
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

    private int id;
    private int itemsId;
    private String itemsBarcode;
    private double quantityForUnit;
    private ObjectProperty<UnitsModel> unitsModel = new SimpleObjectProperty<>();

    /**
     * What this unit costs and sells for, as a whole. Zero means the unit has no
     * price of its own and is priced as the item's price times
     * {@link #quantityForUnit} - which is what a unit that nobody priced has
     * always done. The three selling prices mirror the item's, since the tier a
     * customer is on has to answer for a carton as much as for a piece.
     */
    private DoubleProperty buyPrice = new SimpleDoubleProperty();
    private double selPrice;
    private DoubleProperty selPrice2 = new SimpleDoubleProperty();
    private DoubleProperty selPrice3 = new SimpleDoubleProperty();

    public double getBuyPrice() {
        return buyPrice.get();
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice.set(buyPrice);
    }

    public DoubleProperty buyPriceProperty() {
        return buyPrice;
    }

    public double getSelPrice() {
        return selPrice;
    }

    public void setSelPrice(double selPrice) {
        this.selPrice = selPrice;
    }

    public double getSelPrice2() {
        return selPrice2.get();
    }

    public void setSelPrice2(double selPrice2) {
        this.selPrice2.set(selPrice2);
    }

    public DoubleProperty selPrice2Property() {
        return selPrice2;
    }

    public double getSelPrice3() {
        return selPrice3.get();
    }

    public void setSelPrice3(double selPrice3) {
        this.selPrice3.set(selPrice3);
    }

    public DoubleProperty selPrice3Property() {
        return selPrice3;
    }

    public String getItemsBarcode() {
        return itemsBarcode;
    }

    public void setItemsBarcode(String itemsBarcode) {
        this.itemsBarcode = itemsBarcode;
    }

    public double getQuantityForUnit() {
        return quantityForUnit;
    }

    public void setQuantityForUnit(double quantityForUnit) {
        this.quantityForUnit = quantityForUnit;
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
