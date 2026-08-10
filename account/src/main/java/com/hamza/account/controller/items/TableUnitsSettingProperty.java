package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsUnitsModel;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

public class TableUnitsSettingProperty {
    private final StringProperty selectedType = new SimpleStringProperty();
    private final StringProperty textUnitBarcode = new SimpleStringProperty();
    /** How many base units this one holds, for this item only. */
    private final StringProperty textUnitQuantity = new SimpleStringProperty();
    /**
     * What this unit costs and sells for as a whole. Left empty, the unit has no
     * price of its own and is priced as the item's price times the quantity.
     */
    private final StringProperty textUnitBuyPrice = new SimpleStringProperty();
    private final StringProperty textUnitSelPrice = new SimpleStringProperty();
    private final StringProperty textUnitSelPrice2 = new SimpleStringProperty();
    private final StringProperty textUnitSelPrice3 = new SimpleStringProperty();
    private final ListProperty<ItemsUnitsModel> itemsUnitsModelList = new SimpleListProperty<>();

    public String getSelectedType() {
        return selectedType.get();
    }

    public void setSelectedType(String selectedType) {
        this.selectedType.set(selectedType);
    }

    public StringProperty selectedTypeProperty() {
        return selectedType;
    }

    public String getTextUnitBarcode() {
        return textUnitBarcode.get();
    }

    public void setTextUnitBarcode(String textUnitBarcode) {
        this.textUnitBarcode.set(textUnitBarcode);
    }

    public StringProperty textUnitBarcodeProperty() {
        return textUnitBarcode;
    }

    public String getTextUnitQuantity() {
        return textUnitQuantity.get();
    }

    public void setTextUnitQuantity(String textUnitQuantity) {
        this.textUnitQuantity.set(textUnitQuantity);
    }

    public StringProperty textUnitQuantityProperty() {
        return textUnitQuantity;
    }

    public String getTextUnitBuyPrice() {
        return textUnitBuyPrice.get();
    }

    public StringProperty textUnitBuyPriceProperty() {
        return textUnitBuyPrice;
    }

    public String getTextUnitSelPrice() {
        return textUnitSelPrice.get();
    }

    public StringProperty textUnitSelPriceProperty() {
        return textUnitSelPrice;
    }

    public String getTextUnitSelPrice2() {
        return textUnitSelPrice2.get();
    }

    public StringProperty textUnitSelPrice2Property() {
        return textUnitSelPrice2;
    }

    public String getTextUnitSelPrice3() {
        return textUnitSelPrice3.get();
    }

    public StringProperty textUnitSelPrice3Property() {
        return textUnitSelPrice3;
    }

    public ObservableList<ItemsUnitsModel> getItemsUnitsModelList() {
        return itemsUnitsModelList.get();
    }

    public void setItemsUnitsModelList(ObservableList<ItemsUnitsModel> itemsUnitsModelList) {
        this.itemsUnitsModelList.set(itemsUnitsModelList);
    }

    public ListProperty<ItemsUnitsModel> itemsUnitsModelListProperty() {
        return itemsUnitsModelList;
    }

}
