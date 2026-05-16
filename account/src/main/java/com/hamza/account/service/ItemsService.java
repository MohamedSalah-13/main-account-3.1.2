package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record ItemsService(DaoFactory daoFactory) {

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

    public int insertList(List<ItemsModel> list) throws DaoException {
        return daoFactory.getItemsDao().insertList(list);
    }

    public int deleteItem(int id) throws DaoException {
        return daoFactory.getItemsDao().deleteById(id);
    }

    public int getMaxItemId() {
        return daoFactory.getItemsDao().maxItemId();
    }


    public ItemsModel findItemById(int id) throws DaoException {
        return daoFactory.getItemsDao().findItemById(id);
    }

    public List<ItemsModel> getFilterItems(String newValue) throws DaoException {
        return daoFactory.getItemsDao().getFilterItems(newValue);
    }


    public List<ItemsModel> getProducts(int rowsPerPage, int offset) throws DaoException {
        return daoFactory.getItemsDao().getProducts(rowsPerPage, offset);
    }

    public int getCountItems() {
        return daoFactory.getItemsDao().getCountItems();
    }

    public List<ItemsModel> getMainItemsListWithoutInactiveByMainGroupId(int mainGroupId) throws DaoException {
        return daoFactory.getItemsDao().getItemsByMainGroupId(mainGroupId).stream()
                .filter(ItemsModel::isActiveItem).toList();
    }

    public List<ItemsModel> searchAvailableItemsByStockId(int stockId, String searchText, int limit) throws DaoException {
        if (stockId <= 0) {
            throw new DaoException("رقم المخزن غير صحيح");
        }

        return daoFactory.getItemsDao().searchAvailableItemsByStockId(stockId, searchText, limit);
    }
}
