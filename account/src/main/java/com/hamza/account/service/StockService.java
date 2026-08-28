package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.DefaultStock;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
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

    public List<Stock> getStocks() throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_SHOW);
        return daoFactory.stockDao().loadAll();
    }

    public int save(Stock stock) throws DaoException {
        AuthorizationGuard.require(stock != null && stock.getId() > 0
                ? AppPermissions.STOCK_UPDATE : AppPermissions.STOCK_CREATE);
        if (stock == null || stock.getName() == null || stock.getName().isBlank())
            throw new UserValidationException(LanguageManager.getInstance().getString("stocks.error.name.required"));
        if (stock.getId() > 0) {
            return daoFactory.stockDao().update(stock);
        }
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

    public int delete(int stockId) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.STOCKS, stockId, daoFactory.stockDao()::deleteById)
                .rowsOrThrow();
    }

    public Stock getDefaultStock() throws DaoException {
        return daoFactory.stockDao().getDataById(DefaultStock.ID);
    }
}
