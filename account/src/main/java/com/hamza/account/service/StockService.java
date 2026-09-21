package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.items.StockScope;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;

/**
 * The warehouses the application writes {@code stock_id} against.
 * <p>
 * Multi-warehouse support returned as {@code features/stocktransfer} and the
 * "إدارة المخازن" screen; this is where both reach the {@code stocks} table.
 * {@link #getDefaultStock()} stays for the reports and screens that have not
 * been given a warehouse picker yet - see {@link DefaultStock}.
 */
public record StockService(DaoFactory daoFactory) {

    /** The warehouses list for the screen that manages them - which is what {@code stock.show} means. */
    public List<Stock> getStocks() throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_SHOW);
        return daoFactory.stockDao().loadAll();
    }

    /**
     * The warehouses a combo offers: the invoice, the inventory, the item card, the stock count,
     * the transfer and the price check each ask "which warehouse", and none of them is a request
     * to open the warehouses screen.
     * <p>
     * Deliberately unguarded, the way {@code EmployeeService.delegates} is, and the list of tills
     * the same screens offer. Every one of those screens is guarded by a permission
     * of its own, and {@code stock.show} was granted by no migration to anybody: when the pickers
     * read {@link #getStocks()}, a cashier who had sold for years - and the seeded
     * {@code DEFAULT_SALES_CASHIER} itself - could not open the sales screen after upgrading.
     * {@code InvoiceScreenPermissionArchitectureTest} keeps the invoice screen off guarded reads.
     */
    public List<Stock> stocksForPicker(StockScope scope) throws DaoException {
        List<Stock> stocks = daoFactory.stockDao().loadAll();
        return scope == StockScope.EVERYONE ? stocks : stocks.stream().filter(Stock::isActive).toList();
    }

    /**
     * One warehouse by id, whether in use or not - for a document reopened on the warehouse it was
     * saved in, which may have been switched off since. Unguarded for the reason
     * {@link #stocksForPicker} is.
     */
    public Stock stock(int stockId) throws DaoException {
        return daoFactory.stockDao().getDataById(stockId);
    }

    public int save(Stock stock) throws DaoException {
        AuthorizationGuard.require(stock != null && stock.getId() > 0
                ? AppPermissions.STOCK_UPDATE : AppPermissions.STOCK_CREATE);
        if (stock == null || stock.getName() == null || stock.getName().isBlank())
            throw new UserValidationException(LanguageManager.getInstance().getString("stocks.error.name.required"));
        if (stock.getId() > 0) {
            return daoFactory.stockDao().update(stock);
        }
        // Who creates a warehouse is said here, at the moment it is saved - the model used to take
        // whoever was signed in when it was constructed.
        stock.setUserId(CurrentUser.get().getId());
        // quantity_items_table is built from items_stock, not items, so a warehouse
        // with no row for an existing item would show that item as absent rather
        // than at zero. Backfilling here, in the same transaction as the insert, is
        // what InvoiceExpiryService, InventoryDao and the card screen all rely on
        // already having a row to read.
        return TransactionTemplate.execute(() -> {
            int id = daoFactory.stockDao().insertReturningId(stock);
            daoFactory.getItemsStockDao().insertForAllItems(id);
            return id;
        });
    }

    /**
     * Switches a warehouse off, or back on (V77). A switched-off warehouse keeps every movement it
     * ever had and takes no new one; it is what a warehouse becomes instead of being deleted, which
     * {@code DeleteRegistry.STOCKS} refuses for any warehouse that has ever held an item.
     * <p>
     * Switching off is refused, in this order: for the default warehouse, which every screen offers
     * first and every opening balance is written to; for a warehouse with a draft count open, which
     * could then be neither posted nor discarded from the screen; and for a warehouse still holding a
     * balance of anything, which would stay on the books and out of every picker - the shop moves it
     * out with a transfer first. Switching back on is never refused.
     */
    public void setActive(int stockId, boolean active) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_UPDATE);
        if (!active) {
            if (stockId == DefaultStock.ID) {
                throw new BusinessRuleException(message("stocks.error.deactivate.default"));
            }
            if (daoFactory.stockCountDao().findOpenDraft(stockId) != null) {
                throw new BusinessRuleException(message("stocks.error.deactivate.draft.count"));
            }
            int holding = daoFactory.warehouseStockDao().itemsHolding(stockId);
            if (holding > 0) {
                throw new BusinessRuleException(message("stocks.error.deactivate.holds.stock", holding));
            }
        }
        daoFactory.stockDao().updateActive(stockId, active);
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    public int delete(int stockId) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.STOCKS, stockId, daoFactory.stockDao()::deleteById)
                .rowsOrThrow();
    }

    public Stock getDefaultStock() throws DaoException {
        return daoFactory.stockDao().getDataById(DefaultStock.ID);
    }
}
