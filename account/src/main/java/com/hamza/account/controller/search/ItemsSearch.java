package com.hamza.account.controller.search;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;

import java.util.List;

public class ItemsSearch implements SearchInterface<ItemsModel> {

    private final ItemsService itemsService;

    public ItemsSearch(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @Override
    public Class<? super ItemsModel> getSearchClass() {
        return ItemsModel.class;
    }

    @Override
    public String getName(ItemsModel itemsModel) {
        return itemsModel.getNameItem();
    }

    @Override
    public List<ItemsModel> getFilterItems(String filter) throws Exception {
        return itemsService.getFilterItems(filter);
    }

}