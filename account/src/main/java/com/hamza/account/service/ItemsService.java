package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemImageContent;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

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
     * The first of these codes that already belongs to some other item - as its
     * barcode, one of its extra barcodes, or the code on one of its units - or
     * {@code null} if all of them are free. Pass the id of the item being edited
     * so its own codes do not count against it.
     * <p>
     * One query for the whole candidate list: a blank or duplicate entry is
     * dropped before it reaches the database rather than asked about twice.
     */
    public String firstBarcodeTakenByAnotherItem(List<String> codes, int itemId) throws DaoException {
        var candidates = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return daoFactory.getItemsDao().firstBarcodeTakenByAnotherItem(candidates, itemId);
    }

    /**
     * The name of the item already holding {@code code}, or {@code null} if it is
     * free. {@code itemId} is the item being edited, so its own codes do not count
     * against it.
     * <p>
     * This is the one-code question the item screen asks while the user is still
     * typing; {@link #firstBarcodeTakenByAnotherItem} is the whole-set question the
     * save asks. Both are needed - the first is a hint the moment a code is entered,
     * the second is the rule, applied where the row is written.
     */
    public String itemNameHoldingBarcode(String code, int itemId) throws DaoException {
        if (code == null || code.isBlank()) {
            return null;
        }
        return daoFactory.getItemsDao().itemNameHoldingBarcode(code.trim(), itemId);
    }

    public int updateItem(ItemsModel itemsModel) throws DaoException {
        AuthorizationGuard.require(itemsModel.getId() == 0 ? AppPermissions.ITEMS_CREATE : AppPermissions.ITEMS_UPDATE);
        requireSellAboveBuy(itemsModel);
        if (itemsModel.getId() == 0)
            return daoFactory.getItemsDao().insert(itemsModel);
        else
            return daoFactory.getItemsDao().update(itemsModel);
    }

    public int commitItemUpdate(ItemsModel itemsModel) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        requireSellAboveBuy(itemsModel);
        return daoFactory.getItemsDao().update(itemsModel);
    }

    /**
     * Saves only the fields editable from the item list. The full item update replaces
     * units and extra barcodes, neither of which belongs to an in-table edit.
     */
    public int quickUpdate(ItemsModel itemsModel) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        if (itemsModel.getNameItem() == null || itemsModel.getNameItem().isBlank()) {
            throw new BusinessRuleException(LanguageManager.getInstance().getString("item.error.name.required"));
        }
        List<String> barcode = itemsModel.getBarcode() == null ? List.of() : List.of(itemsModel.getBarcode());
        String duplicateBarcode = firstBarcodeTakenByAnotherItem(barcode, itemsModel.getId());
        if (duplicateBarcode != null) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("item.error.barcode.used.by.other", duplicateBarcode));
        }
        requireSellAboveBuy(itemsModel);
        return daoFactory.getItemsDao().quickUpdate(itemsModel);
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

    /**
     * Zero markup is not a price; it is a loss on paper the moment the item
     * moves. {@code AddItemController} already refuses this before the user can
     * click save, but that is a hint on one screen - this is the same rule
     * applied where the write actually happens, so a caller other than that
     * screen is held to it too.
     * <p>
     * Deliberately not applied to {@link #updateGroup}: it takes a whole batch
     * where an item having its group changed, say, is indistinguishable here
     * from one having its price changed, and this codebase has no guarantee
     * every item already on file satisfies the rule. Applying it there would
     * refuse an unrelated bulk edit - reactivating a batch of items, say -
     * because one of them happens to carry price data older than this rule.
     */
    private static void requireSellAboveBuy(ItemsModel model) throws DaoException {
        if (model.getSelPrice1() <= model.getBuyPrice()) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("item.error.sell.not.above.buy.named", model.getNameItem()));
        }
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

    /**
     * The three reads behind a screen that lists many items at once.
     * <p>
     * They answer the same rows as {@link #getFilterItems}, {@link #getProducts} and
     * {@link #getAllProducts}, but mapped for display: no per-row lookup of the group,
     * the unit, the unit list or the extra barcodes, so a page costs a fixed handful of
     * queries instead of one per item times the number of associations it has.
     * <p>
     * The models they return are display rows. Edit one through {@link #quickUpdate} or
     * {@link #updateItemImage}; a screen editing the whole item loads it again through
     * {@link #findItemById}.
     */
    public List<ItemsModel> getCatalogProducts(ItemCatalogFilter filter, int rowsPerPage, int offset)
            throws DaoException {
        return daoFactory.getItemsDao().getCatalogProducts(filter, rowsPerPage, offset);
    }

    /** How many rows the same filter matches in total. */
    public int getCatalogCount(ItemCatalogFilter filter) throws DaoException {
        return daoFactory.getItemsDao().getCatalogCount(filter);
    }

    public List<ItemsModel> getAllCatalogProducts() throws DaoException {
        return daoFactory.getItemsDao().getAllCatalogProducts();
    }

    public ItemsModel getCatalogItem(int id) throws DaoException {
        return daoFactory.getItemsDao().getCatalogItem(id);
    }

    /** The picture is deliberately absent from catalog rows and read only on demand. */
    public ItemImageContent getItemImage(int itemId) throws DaoException {
        return ItemImageContent.of(daoFactory.getItemsDao().getItemImage(itemId));
    }

    /**
     * Saves only an item's picture. The items list edits pictures on rows that carry no
     * units and no extra barcodes, and the full item update replaces both from the model.
     */
    public int updateItemImage(int itemId, byte[] image) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        return daoFactory.getItemsDao().updateImage(itemId, image);
    }


    public List<ItemsModel> getMainItemsListWithoutInactiveByMainGroupId(int mainGroupId) throws DaoException {
        return daoFactory.getItemsDao().getItemsByMainGroupId(mainGroupId).stream()
                .filter(ItemsModel::isActiveItem).toList();
    }
}
