package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Items_Stock_Model;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class Items_StockDao extends AbstractDao<Items_Stock_Model> {

    private final String TABLE_NAME = "items_stock";
    private final String ID = "id";
    private final String ITEMS_ID = "item_id";
    private final String STOCK_ID = "stock_id";
    private final String FIRST_BALANCE = "first_balance";
    private final String currentQuantity = "current_quantity";
    private final DaoFactory daoFactory;


    public Items_StockDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }


    @Override
    public int insert(Items_Stock_Model model) throws DaoException {
        String sql = SqlStatements.insertStatement(
                "items_stock",
                "item_id",
                "stock_id",
                "first_balance",
                "current_quantity"
        );

        return executeUpdate(sql, getData(model));
    }

    @Override
    public Object[] getData(Items_Stock_Model model) {
        return new Object[]{
                model.getItemsModel().getId(),
                model.getStock().getId(),
                model.getFirstBalance(),
                model.getCurrentQuantity()
        };
    }


    @Override
    public Items_Stock_Model map(ResultSet rs) throws DaoException {
        Items_Stock_Model stockModel = new Items_Stock_Model();
        try {
            int stockId = rs.getInt(STOCK_ID);
            stockModel.setId(rs.getInt(ID));
            stockModel.setItemsModel(daoFactory.getItemsDao().findItemByIdAndStockId(rs.getInt(ITEMS_ID), stockId));
            stockModel.setStock(new Stock(stockId, rs.getString(StockDao.STOCK_NAME)));
            stockModel.setFirstBalance(rs.getDouble(FIRST_BALANCE));
            stockModel.setCurrentQuantity(rs.getDouble(currentQuantity));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return stockModel;
    }

    public int insertOrUpdate(Items_Stock_Model model) throws DaoException {
        String sql = """
                INSERT INTO items_stock
                (
                    item_id,
                    stock_id,
                    first_balance,
                    current_quantity
                )
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    first_balance = VALUES(first_balance),
                    current_quantity = VALUES(current_quantity)
                """;

        return executeUpdate(sql,
                model.getItemsModel().getId(),
                model.getStock().getId(),
                model.getFirstBalance(),
                model.getCurrentQuantity()
        );
    }

    public int insertOrUpdateList(List<Items_Stock_Model> list) throws DaoException {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Items_Stock_Model model : list) {
            if (model == null || model.getItemsModel() == null || model.getStock() == null) {
                continue;
            }

            if (model.getFirstBalance() < 0 || model.getCurrentQuantity() < 0) {
                throw new DaoException("لا يمكن إدخال رصيد مخزن أقل من صفر");
            }

            count += insertOrUpdate(model);
        }

        return count;
    }

    public List<Items_Stock_Model> getAllByItemId(int itemId) throws DaoException {
        String sql = """
                SELECT
                    ist.id,
                    ist.item_id,
                    ist.stock_id,
                    ist.first_balance,
                    ist.current_quantity,
                    s.stock_name
                FROM items_stock ist
                         JOIN stocks s ON s.stock_id = ist.stock_id
                WHERE ist.item_id = ?
                ORDER BY s.stock_name
                """;

        return queryForObjects(sql, this::map, itemId);
    }

    public int deleteByItemId(int itemId) throws DaoException {
        String sql = "DELETE FROM items_stock WHERE item_id = ?";
        return executeUpdate(sql, itemId);
    }

    public int deleteZeroOpeningBalancesByItemId(int itemId) throws DaoException {
        String sql = """
                DELETE FROM items_stock
                WHERE item_id = ?
                  AND first_balance = 0
                  AND current_quantity = 0
                """;
        return executeUpdate(sql, itemId);
    }
}
