package com.hamza.account.features.inventory;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/**
 * What the inventory screen asks the database for.
 * <p>
 * It exists so the sheet stops borrowing {@code ItemsService.getProducts}, which is
 * the item-editing query: every question the screen had to ask was answered by
 * loading editable item models and then doing arithmetic on whatever happened to be
 * in memory. Growing the screen - a filter, an export, a stock-take - meant either
 * widening that query for everyone or computing more in the controller.
 * <p>
 * Nothing here is thread-confined; the DAO borrows a pooled connection per call, so
 * the screen runs these on a background {@code Task} and must.
 */
public record InventoryService(DaoFactory daoFactory) {

    /**
     * One load of the screen: the rows for {@code query}'s page, and the totals for
     * every item the query matches.
     * <p>
     * Both come from the same {@link InventoryQuery} on the same thread, one after
     * the other, so the header and the table always describe the same set. Two
     * queries in total, where the screen used to run a couple of hundred.
     */
    public InventoryPage load(InventoryQuery query) throws DaoException {
        InventoryDao dao = daoFactory.inventoryDao();
        InventorySummary summary = dao.summary(query);
        List<InventoryRow> rows = dao.rows(query);
        return new InventoryPage(rows, summary);
    }

    /**
     * Every row the query matches, ignoring its page - what printing and export need.
     */
    public List<InventoryRow> loadAll(InventoryQuery query) throws DaoException {
        return daoFactory.inventoryDao().allRows(query);
    }

    /** The totals alone, for callers that do not need the rows. */
    public InventorySummary summary(InventoryQuery query) throws DaoException {
        return daoFactory.inventoryDao().summary(query);
    }

    /** Every item's balance in every warehouse, for the cross-warehouse comparison report. */
    public List<StockBalanceRow> crossWarehouseBalances() throws DaoException {
        return daoFactory.inventoryDao().crossWarehouseBalances();
    }
}
