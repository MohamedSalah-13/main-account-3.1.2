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
    //    private final StringProperty textUnitQuantity = new SimpleStringProperty();
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
