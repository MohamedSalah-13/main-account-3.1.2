package com.hamza.account.model.dao;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;

/**
 * The writes of {@code items_stock}: a row per item per warehouse, and the opening balance it holds.
 * <p>
 * Since V78 that opening is the only one there is. It used to have a second home on the item row,
 * which a trigger copied into warehouse 1 on every update of the item, and a third column,
 * {@code current_quantity}, that something wrote and nothing ever read as a figure. The model this
 * DAO once mapped rows into, and the finder and the inserts that went with it, had no caller left.
 */
public class Items_StockDao extends AbstractDao<Void> {

    /** A new item gets a row in every warehouse, the opening in the default one and zero elsewhere. */
    public int insertForAllStocks(int itemId, int defaultStockId, double openingBalance) throws DaoException {
        String sql = """
                INSERT INTO items_stock (item_id, stock_id, first_balance)
                SELECT ?, s.stock_id, CASE WHEN s.stock_id = ? THEN ? ELSE 0 END
                FROM stocks s
                """;
        return executeUpdate(sql, itemId, defaultStockId, openingBalance);
    }

    /**
     * The other direction of {@link #insertForAllStocks}: a warehouse created after
     * items already exist has none of their rows, so {@code quantity_items_table} -
     * built from {@code items_stock}, not {@code items} - would show it as empty
     * however much stock a transfer moves into it. Every item starts this warehouse
     * at zero; there is no history to backfill for one that did not exist yet.
     */
    public int insertForAllItems(int stockId) throws DaoException {
        String sql = """
                INSERT INTO items_stock (item_id, stock_id, first_balance)
                SELECT i.id, ?, 0
                FROM items i
                """;
        return executeUpdate(sql, stockId);
    }

    /**
     * Writes one warehouse's opening for one item. Whether it may is not decided here:
     * {@code WarehouseOpeningBalance} is asked first by every caller.
     */
    public int updateOpeningBalance(int itemId, int stockId, double openingBalance) throws DaoException {
        return executeUpdate("UPDATE items_stock SET first_balance=? WHERE item_id=? AND stock_id=?",
                openingBalance, itemId, stockId);
    }

    @Override
    public Void map(ResultSet rs) {
        throw new UnsupportedOperationException("This DAO writes rows and maps none");
    }
}
