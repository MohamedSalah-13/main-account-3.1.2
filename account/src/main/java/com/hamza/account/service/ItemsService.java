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

    /**
     * Whether this code already belongs to some other item - as its barcode, one
     * of its extra barcodes, or the code on one of its units. Pass the id of the
     * item being edited so its own codes do not count against it.
     */
    /**
     * The name of the item already holding {@code code}, or {@code null} if it is free.
     * {@code itemId} is the item being edited, so its own codes do not count against it.
     * <p>
     * This is the question the item screen asks while the user is still typing;
     * {@link #isBarcodeTakenByAnotherItem} is the same question the save asks. Both are
     * needed - the first is a hint the moment a code is entered, the second is the rule,
     * applied where the row is written.
     */
    public String itemNameHoldingBarcode(String code, int itemId) throws DaoException {
        if (code == null || code.isBlank()) {
            return null;
        }
        return daoFactory.getItemsDao().itemNameHoldingBarcode(code.trim(), itemId);
    }

    public boolean isBarcodeTakenByAnotherItem(String barcode, int itemId) throws DaoException {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        return daoFactory.getItemsDao().barcodeExists(barcode.trim(), itemId);
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
}
