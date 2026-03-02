package com.hamza.account.service;

import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record ItemsService(DaoFactory daoFactory, ServiceData serviceData) {

    public ItemsModel getItemByItemIdAndStockId(Integer itemId, Integer stockId) throws DaoException {
        return daoFactory.getItemsDao().findItemByIdAndStockId(itemId, stockId);
    }

    public ItemsModel getItemByItemNameAndStockId(String itemName, Integer stockId) throws DaoException {
        return daoFactory.getItemsDao().findItemByStockIdAndName(itemName, stockId);
    }

    public ItemsModel getItemByBarcodeAndStockId(String barcode, Integer stockId) throws DaoException {
        return daoFactory.getItemsDao().findItemByStockIdAndBarcode(barcode, stockId);
    }


    public int updateItem(ItemsModel itemsModel) throws DaoException {
        if (itemsModel.getId() == 0)
            return daoFactory.getItemsDao().insert(itemsModel);
        else
            return daoFactory.getItemsDao().update(itemsModel);
    }

    public int commitItemUpdate(ItemsModel itemsModel) throws DaoException {
        return daoFactory.getItemsDao().update(itemsModel);
    }

    public int updateGroup(List<ItemsModel> itemsModel) throws DaoException {
        return daoFactory.getItemsDao().updateList(itemsModel);
    }

    public int deleteItem(int id) throws DaoException {
        return daoFactory.getItemsDao().deleteById(id);
    }

    public int getMaxItemId() {
        return daoFactory.getItemsDao().maxItemId();
    }

    public List<ItemsModel> maxItemsSold() throws DaoException {
        // get all total sales in current month
        var listByCurrentMonth = serviceData.getTotalSalesService().getListByCurrentMonth()
                .stream()
                .map(BaseTotals::getId).toList();
        // get all items id
        var betweenTwoInvoiceNumber = serviceData.getSalesService().findBetweenTwoInvoiceNumber(listByCurrentMonth.getFirst(), listByCurrentMonth.getLast())
                .stream()
                .toList();

        List<ItemsModel> itemsModels = new ArrayList<>();
        betweenTwoInvoiceNumber.stream()
                .collect(Collectors.groupingBy(sales -> sales.getItems().getId(),
                        Collectors.summingDouble(Sales::getQuantityByUnit)))
                .forEach((itemId, total) -> {
                    try {
                        ItemsModel itemsModel = getItemByItemIdAndStockId(itemId, 1);
                        itemsModel.setId(itemId);
//                    itemsModel.setNameItem(sales.getItems().getName());
                        itemsModel.setSumSales(total);
                        itemsModels.add(itemsModel);
                    } catch (DaoException e) {
                        throw new RuntimeException(e);
                    }
                });

        return itemsModels.stream()
                .sorted(Comparator.comparing(ItemsModel::getSumSales).reversed()).limit(5).toList();
    }

    public List<ItemsModel> filterItemListsByStockName(String stockName) {
        if (stockName == null) return getMainItemsListWithoutInactive();
        return getMainItemsListWithoutInactive().stream().filter(itemsModel -> itemsModel.getItemStock().getName().equals(stockName)).toList();
    }

    public List<ItemsModel> getListItemsInMainStock() {
        return getMainItemsList().stream().filter(itemsModel -> itemsModel.getItemStock().getId() == 1).toList();
    }

    public List<ItemsModel> getMainItemsListWithoutInactive() {
        return getMainItemsList().stream()
                .filter(ItemsModel::isActiveItem).toList();
    }

    public List<ItemsModel> getMainItemsListWithoutInactiveByMainGroupId(int mainGroupId) throws DaoException {
        return daoFactory.getItemsDao().getItemsByMainGroupId(mainGroupId).stream()
                .filter(ItemsModel::isActiveItem).toList();
    }

    public List<ItemsModel> getMainItemsList() {
        return LoadDataAndList.getItemsModelList();
    }
}
