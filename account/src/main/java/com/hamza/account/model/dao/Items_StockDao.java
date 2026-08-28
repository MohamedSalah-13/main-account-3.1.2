package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Items_Stock_Model;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class Items_StockDao extends AbstractDao<Items_Stock_Model> {

    private final String TABLE_NAME = "items_stock";
    private final String ID = "id";
    private final String ITEMS_ID = "item_id";
    private final String STOCK_ID = "stock_id";
    private final String FIRST_BALANCE = "first_balance";
    private final String currentQuantity = "current_quantity";
    private final DaoFactory daoFactory;


    public Items_StockDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    public Optional<Items_Stock_Model> findItemsStockByItemIdAndStockId(int itemId, int stockId) throws DaoException {
        String query = " SELECT * from items_stock i join stocks s on s.stock_id = i.stock_id\n" +
                "where item_id =? and i.stock_id =? ";
        return Optional.ofNullable(queryForObject(query, this::map, itemId, stockId));
    }

    public int insertWithException(Items_Stock_Model itemsStockModel) throws SQLException {
        String insert = SqlStatements.insertStatement(TABLE_NAME, ITEMS_ID, STOCK_ID, FIRST_BALANCE, currentQuantity);
        Object[] objects = new Object[]{itemsStockModel.getItemsModel().getId(), itemsStockModel.getStock().getId(), itemsStockModel.getFirstBalance(), itemsStockModel.getCurrentQuantity()};
        return executeUpdateWithException(insert, objects);
    }

    @Override
    public int insert(Items_Stock_Model itemsStockModel) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, ITEMS_ID, STOCK_ID, FIRST_BALANCE, currentQuantity)
                , itemsStockModel.getItemsModel().getId(), itemsStockModel.getStock().getId(), itemsStockModel.getFirstBalance(), itemsStockModel.getCurrentQuantity());
    }

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

    public int updateOpeningBalance(int itemId, int stockId, double openingBalance) throws DaoException {
        return executeUpdate("UPDATE items_stock SET first_balance=? WHERE item_id=? AND stock_id=?",
                openingBalance, itemId, stockId);
    }

    @Override
    public Items_Stock_Model map(ResultSet rs) throws DaoException {
        Items_Stock_Model stockModel = new Items_Stock_Model();
        try {
            int stockId = rs.getInt(STOCK_ID);
            double firstBalance = rs.getDouble(FIRST_BALANCE);
            stockModel.setId(rs.getInt(ID));
            stockModel.setItemsModel(daoFactory.getItemsDao().findItemByIdAndStockId(rs.getInt(ITEMS_ID), stockId));
            stockModel.setStock(new Stock(stockId, rs.getString(StockDao.STOCK_NAME)));
            stockModel.setFirstBalance(firstBalance);
            stockModel.setCurrentQuantity(rs.getDouble(currentQuantity));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return stockModel;
    }
}
