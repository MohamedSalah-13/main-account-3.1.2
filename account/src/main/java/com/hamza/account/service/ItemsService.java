package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
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
    public boolean isBarcodeTakenByAnotherItem(String barcode, int itemId) throws DaoException {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        return daoFactory.getItemsDao().barcodeExists(barcode.trim(), itemId);
    }

    public int updateItem(ItemsModel itemsModel) throws DaoException {
        AuthorizationGuard.require(itemsModel.getId() == 0 ? AppPermissions.ITEMS_CREATE : AppPermissions.ITEMS_UPDATE);
        if (itemsModel.getId() == 0)
            return daoFactory.getItemsDao().insert(itemsModel);
        else
            return daoFactory.getItemsDao().update(itemsModel);
    }

    public int commitItemUpdate(ItemsModel itemsModel) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        return daoFactory.getItemsDao().update(itemsModel);
    }

    /**
     * Whether this item's opening balance is closed to editing, which it is as soon as
     * anything has moved it - an invoice line, a return, a transfer or a stock count.
     * <p>
     * The item screen asks so it can grey the field and say why. The rule itself lives
     * in the DAO and is applied where the row is written, because a disabled field is a
     * hint and not a rule: the same save is reachable from the Excel import and from
     * any caller that never looked at the screen.
     */
    public boolean isOpeningBalanceLocked(int itemId) throws DaoException {
        return daoFactory.getItemsDao().isOpeningBalanceLocked(itemId);
    }

    public int updateGroup(List<ItemsModel> itemsModel) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        return daoFactory.getItemsDao().updateList(itemsModel);
    }

    public int insertList(List<ItemsModel> list) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_ADD_EXCEL);
        return daoFactory.getItemsDao().insertList(list);
    }

    public int deleteItem(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.ITEMS, id, daoFactory.getItemsDao()::deleteById)
                .rowsOrThrow();
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
