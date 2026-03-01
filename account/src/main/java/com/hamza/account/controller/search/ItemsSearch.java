package com.hamza.account.controller.search;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;

public class ItemsSearch implements SearchInterface<ItemsModel> {

    private final ItemsService itemsService;
    private final StringProperty stockName = new SimpleStringProperty();

    public ItemsSearch(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @Override
    public Class<? super ItemsModel> getSearchClass() {
        return ItemsModel.class;
    }

    @Override
    public List<ItemsModel> searchItems() throws Exception {
        //TODO 11/23/2025 6:55 AM Mohamed: filter to remove item package
        return itemsService.filterItemListsByStockName(stockNameProperty().get());
    }

    @Override
    public String getName(ItemsModel itemsModel) {
        return itemsModel.getNameItem();
    }

//    private List<ItemsModel> getFilteredItemsForDisplay() {
//        var mainItemsListWithoutInactive = itemsService.getMainItemsListWithoutInactive();
//        if (dataInterface.designInterface().showDataForCustomer()) {
//            return mainItemsListWithoutInactive;
//        }
//        return mainItemsListWithoutInactive.stream()
//                .filter(itemsModel -> !itemsModel.isHasPackage())
//                .toList();
//    }

    public String getStockName() {
        return stockName.get();
    }

    public void setStockName(String stockName) {
        this.stockName.set(stockName);
    }

    public StringProperty stockNameProperty() {
        return stockName;
    }
}
