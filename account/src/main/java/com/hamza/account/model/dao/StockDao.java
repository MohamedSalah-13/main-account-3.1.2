package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class StockDao extends AbstractDao<Stock> {

    public static final String STOCK_NAME = "stock_name";
    private final String TABLE_NAME = "stocks";
    private final String STOCK_ID = "stock_id";
    private final String STOCK_ADDRESS = "stock_address";
    private final String USER_ID = "user_id";

    StockDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Stock> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public List<Stock> loadAllById(int id) throws DaoException {
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, STOCK_ID), this::map, id);
    }

    @Override
    public int insert(Stock stock) throws DaoException {
        String s = SqlStatements.insertStatement(TABLE_NAME, STOCK_NAME, STOCK_ADDRESS, USER_ID);
        return executeUpdate(s, getData(stock));
    }

    @Override
    public int update(Stock stock) throws DaoException {
        Object[] strings = {stock.getName(), stock.getAddress(), stock.getId()};
        String update = SqlStatements.updateStatement(TABLE_NAME, STOCK_ID, STOCK_NAME, STOCK_ADDRESS);
        return executeUpdate(update, strings);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid stock ID: " + id);
        if (id == 1)
            throw new IllegalArgumentException("Cannot delete stock with ID 1");
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, STOCK_ID), id);
    }

    @Override
    public Stock getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, STOCK_ID), this::map, id);
    }

    @Override
    public Stock getDataByString(String stockName) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, STOCK_NAME), this::map, stockName);
    }

    @Override
    public Object[] getData(Stock stock) {
        return new Object[]{stock.getName(), stock.getAddress(), stock.getUsers().getId()};
    }

    @Override
    public Stock map(ResultSet rs) throws DaoException {
        Stock model;
        try {
            model = new Stock(rs.getInt(STOCK_ID), rs.getString(STOCK_NAME), rs.getString(STOCK_ADDRESS));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }
}
