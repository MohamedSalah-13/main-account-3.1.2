package com.hamza.account.model.dao;

import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class StockTransferListDao extends AbstractDao<StockTransferListItems> {

    public static final String TABLE_NAME = "stock_transfer_list";
    public static final String STOCK_TRANSFER_ID = "stock_transfer_id";
    private final String BASE_FETCH_QUERY = "SELECT * from stock_transfer_list join items i on stock_transfer_list.item_id = i.id";
    private final String FETCH_QUERY_WITH_ID = BASE_FETCH_QUERY + " WHERE stock_transfer_id=?";
    private final String ID = "id";
    private final String ITEM_ID = "item_id";
    private final String QUANTITY = "quantity";
    private final DaoFactory daoFactory;

    StockTransferListDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<StockTransferListItems> loadAll() throws DaoException {
        return executeFetchQuery(BASE_FETCH_QUERY);
    }

    @Override
    public List<StockTransferListItems> loadAllById(int id) throws DaoException {
        return executeFetchQuery(FETCH_QUERY_WITH_ID, id);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Object[] getData(StockTransferListItems stockTransfer) {
        return new Object[]{stockTransfer.getStock_transfer_id(), stockTransfer.getItem().getId(), stockTransfer.getQuantity()};
    }

    @Override
    public StockTransferListItems map(ResultSet rs) throws DaoException {
        StockTransferListItems model = new StockTransferListItems();
        try {
            model.setId(rs.getInt(ID));
            model.setStock_transfer_id(rs.getInt(STOCK_TRANSFER_ID));

            // get stock where convert from
            var dataById = daoFactory.stockTransferDao().getDataByIdWithoutList(rs.getInt(STOCK_TRANSFER_ID));
            model.setItem(daoFactory.getItemsDao().findItemByIdAndStockId(rs.getInt(ITEM_ID), dataById.getStockFrom().getId()));
            model.setQuantity(rs.getDouble(QUANTITY));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }

    @SneakyThrows
    @Override
    public int insertList(List<StockTransferListItems> list) throws DaoException {
        String insert = SqlStatements.insertStatement(TABLE_NAME, STOCK_TRANSFER_ID, ITEM_ID, QUANTITY);
        return executeUpdateListWithException(list, insert, (statement, stockTransfer) -> {
            Object[] objects = getData(stockTransfer);
            for (int i = 0; i < objects.length; i++) {
                statement.setObject(i + 1, objects[i]);
            }
        });
    }

    @SneakyThrows
    @Override
    public int updateList(List<StockTransferListItems> list) {
        String update = SqlStatements.updateStatement(TABLE_NAME, ID, STOCK_TRANSFER_ID, ITEM_ID, QUANTITY);
        return executeUpdateListWithException(list, update, (statement, stockTransfer) -> {
            Object[] objects = new Object[]{stockTransfer.getId(), stockTransfer.getStock_transfer_id(), stockTransfer.getItem().getId(), stockTransfer.getQuantity()};
            for (int i = 0; i < objects.length; i++) {
                statement.setObject(i + 1, objects[i]);
            }
        });
    }

    private List<StockTransferListItems> executeFetchQuery(String query, Object... params) throws DaoException {
        return queryForObjects(query, this::map, params);
    }

}
