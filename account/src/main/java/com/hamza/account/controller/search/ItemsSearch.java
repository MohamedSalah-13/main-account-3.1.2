package com.hamza.account.controller.search;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;

import java.util.List;

public class ItemsSearch implements SearchInterface<ItemsModel> {

    private final ItemsService itemsService;

    public ItemsSearch(ItemsService itemsService) {
        this.itemsService = itemsService;
    }

    @Override
    public List<TableColumn<ItemsModel, ?>> columns() {
        return List.of(
                Columns.number(NamesTables.CODE, ItemsModel::getId),
                Columns.text(NamesTables.STRING, ItemsModel::getBarcode),
                Columns.text(NamesTables.NAME_ITEM, ItemsModel::getNameItem),
                Columns.number(NamesTables.BUY_PRICE, ItemsModel::getBuyPrice),
                Columns.number(NamesTables.SEL_PRICE, ItemsModel::getSelPrice1),
                Columns.number(NamesTables.SEL_PRICE + "2", ItemsModel::getSelPrice2),
                Columns.number(NamesTables.SEL_PRICE + "3", ItemsModel::getSelPrice3),
                Columns.number(NamesTables.MINI_QUANTITY, ItemsModel::getMini_quantity),
                Columns.number(NamesTables.FIRST_BALANCE, ItemsModel::getFirstBalanceForStock),
                Columns.number(NamesTables.SUM_ALL_BALANCE, ItemsModel::getSumAllBalance)
        );
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
