package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Items_Stock_Model;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Items_StockDao extends AbstractDao<Items_Stock_Model> {

    private final String TABLE_NAME = "items_stock";
    private final String ID = "id";
    private final String ITEMS_ID = "item_id";
    private final String STOCK_ID = "stock_id";
    //    private final String FIRST_BALANCE = "first_balance";
    private final String currentQuantity = "current_quantity";
    private final DaoFactory daoFactory;


    public Items_StockDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }


    @Override
    public int insert(Items_Stock_Model itemsStockModel) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, ITEMS_ID, STOCK_ID, currentQuantity)
                , itemsStockModel.getItemsModel().getId(), itemsStockModel.getStock().getId(), itemsStockModel.getCurrentQuantity());
    }

    @Override
    public Items_Stock_Model map(ResultSet rs) throws DaoException {
        Items_Stock_Model stockModel = new Items_Stock_Model();
        try {
            int stockId = rs.getInt(STOCK_ID);
//            double firstBalance = rs.getDouble(FIRST_BALANCE);
            stockModel.setId(rs.getInt(ID));
            stockModel.setItemsModel(daoFactory.getItemsDao().findItemByIdAndStockId(rs.getInt(ITEMS_ID), stockId));
            stockModel.setStock(new Stock(stockId, rs.getString(StockDao.STOCK_NAME)));
//            stockModel.setFirstBalance(firstBalance);
            stockModel.setCurrentQuantity(rs.getDouble(currentQuantity));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return stockModel;
    }
}
