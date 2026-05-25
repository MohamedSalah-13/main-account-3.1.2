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

    public int adjustOpeningBalance(Items_Stock_Model model, int userId) throws DaoException {
        if (model == null || model.getItemsModel() == null || model.getStock() == null) {
            throw new DaoException("بيانات رصيد المخزن غير صحيحة");
        }

        if (model.getFirstBalance() < 0) {
            throw new DaoException("لا يمكن إدخال رصيد أول مدة أقل من صفر");
        }

        int itemId = model.getItemsModel().getId();
        int stockId = model.getStock().getId();
        double newFirstBalance = model.getFirstBalance();

        Items_Stock_Model oldModel = getByItemIdAndStockId(itemId, stockId);

        if (oldModel == null) {
            insert(new Items_Stock_Model(
                    itemId,
                    stockId,
                    newFirstBalance,
                    newFirstBalance
            ));

            if (newFirstBalance > 0) {
                insertOpeningAdjustmentMovement(
                        itemId,
                        stockId,
                        newFirstBalance,
                        0,
                        "إضافة رصيد أول مدة للمخزن",
                        userId
                );
            }

            return 1;
        }

        double oldFirstBalance = oldModel.getFirstBalance();
        double difference = newFirstBalance - oldFirstBalance;

        if (difference == 0) {
            return 0;
        }

        double newCurrentQuantity = oldModel.getCurrentQuantity() + difference;

        if (newCurrentQuantity < 0) {
            throw new DaoException("لا يمكن تعديل رصيد أول المدة لأن الرصيد الحالي سيصبح أقل من صفر");
        }

        String updateSql = """
                UPDATE items_stock
                SET first_balance = ?,
                    current_quantity = current_quantity + ?
                WHERE item_id = ?
                  AND stock_id = ?
                """;

        int result = executeUpdate(updateSql, newFirstBalance, difference, itemId, stockId);

        if (difference > 0) {
            insertOpeningAdjustmentMovement(
                    itemId,
                    stockId,
                    difference,
                    0,
                    "زيادة رصيد أول المدة من " + oldFirstBalance + " إلى " + newFirstBalance,
                    userId
            );
        } else {
            insertOpeningAdjustmentMovement(
                    itemId,
                    stockId,
                    0,
                    Math.abs(difference),
                    "تخفيض رصيد أول المدة من " + oldFirstBalance + " إلى " + newFirstBalance,
                    userId
            );
        }

        return result;
    }

    public int adjustOpeningBalanceList(List<Items_Stock_Model> list, int userId) throws DaoException {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        int count = 0;

        for (Items_Stock_Model model : list) {
            if (model == null || model.getItemsModel() == null || model.getStock() == null) {
                continue;
            }

            count += adjustOpeningBalance(model, userId);
        }

        return count;
    }

    public Items_Stock_Model getByItemIdAndStockId(int itemId, int stockId) throws DaoException {
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
                  AND ist.stock_id = ?
                """;

        return queryForObject(sql, this::map, itemId, stockId);
    }

    private int insertOpeningAdjustmentMovement(
            int itemId,
            int stockId,
            double quantityIn,
            double quantityOut,
            String notes,
            int userId
    ) throws DaoException {
        String sql = """
                INSERT INTO stock_movements
                (
                    item_id,
                    stock_id,
                    movement_type,
                    quantity_in,
                    quantity_out,
                    reference_type,
                    reference_id,
                    notes,
                    user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        String movementType = quantityIn > 0 ? "INVENTORY_ADJUST_IN" : "INVENTORY_ADJUST_OUT";

        return executeUpdate(
                sql,
                itemId,
                stockId,
                movementType,
                quantityIn,
                quantityOut,
                "INVENTORY",
                itemId,
                notes,
                userId
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
