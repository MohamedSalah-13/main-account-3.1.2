package com.hamza.account.model.dao;

import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

@Log4j2
public class StockTransferDao extends AbstractDao<StockTransfer> {

    private final String TABLE_NAME = "stock_transfer";
    private final String ID = "id";
    private final String TRANSFER_DATE = "transfer_date";
    private final String STOCK_FROM = "stock_from";
    private final String STOCK_TO = "stock_to";
    private final String USER_ID = "user_id";
    private final DaoFactory daoFactory;


    StockTransferDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<StockTransfer> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    /**
     * The body of this was empty: the statement was built, nothing ran it, and the
     * method still reported success, so every new transfer was silently discarded.
     * It now writes the header and its items together, mirroring {@link #update}.
     */
    @Override
    public int insert(StockTransfer stockTransfer) throws DaoException {
        String insert = SqlStatements.insertStatement(TABLE_NAME, STOCK_FROM, STOCK_TO, TRANSFER_DATE, USER_ID);
        return insertMultiData(() -> {
            int transferId = insertHeader(insert, stockTransfer);
            stockTransfer.setId(transferId);

            List<StockTransferListItems> transferListItems = stockTransfer.getTransferListItems();
            transferListItems.forEach(item -> item.setStock_transfer_id(transferId));
            daoFactory.stockTransferListDao().insertList(transferListItems);
        });
    }

    @Override
    public int update(StockTransfer stock) throws DaoException {
        return insertMultiData(() -> {
            Object[] strings = {stock.getStockFrom().getId(), stock.getStockTo().getId(), stock.getDate(), stock.getId()};
            String update = SqlStatements.updateStatement(TABLE_NAME, ID, STOCK_FROM, STOCK_TO, TRANSFER_DATE);
            // first update
            executeUpdateWithException(update, strings);
            List<StockTransferListItems> transferListItems = stock.getTransferListItems();
            transferListItems.forEach(stockTransferListItems -> stockTransferListItems.setStock_transfer_id(stock.getId()));
            // second
            executeUpdateWithException(SqlStatements.deleteStatement(StockTransferListDao.TABLE_NAME, StockTransferListDao.STOCK_TRANSFER_ID), stock.getId());
            daoFactory.stockTransferListDao().insertList(transferListItems);
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public StockTransfer getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Object[] getData(StockTransfer stock) {
        return new Object[]{stock.getStockFrom().getId(), stock.getStockTo().getId()
                , stock.getDate(), stock.getUsers().getId()};
    }

    @Override
    public StockTransfer map(ResultSet rs) throws DaoException {
        StockTransfer model = new StockTransfer();
        try {
            model.setId(rs.getInt(ID));
            model.setStockFrom(daoFactory.stockDao().getDataById(rs.getInt(STOCK_FROM)));
            model.setStockTo(daoFactory.stockDao().getDataById(rs.getInt(STOCK_TO)));
            model.setDate(LocalDate.parse(rs.getString(TRANSFER_DATE)));
            model.setTransferListItems(daoFactory.stockTransferListDao().loadAllById(model.getId()));
            // use list not here
        } catch (SQLException e) {
            throw new DaoException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return model;
    }

    /**
     * Writes the transfer row and reads back the id the database generated for it.
     * The list rows carry that id as their foreign key, so it has to be known
     * before they can be written.
     */
    private int insertHeader(String insert, StockTransfer stockTransfer) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                Object[] data = getData(stockTransfer);
                for (int i = 0; i < data.length; i++) {
                    statement.setObject(i + 1, data[i]);
                }

                if (statement.executeUpdate() == 0) {
                    throw new DaoException("لم يتم حفظ تحويل المخزن");
                }

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                    throw new DaoException("لم يتم الحصول على رقم تحويل المخزن الجديد");
                }
            }
        });
    }

    public StockTransfer getDataByIdWithoutList(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::mapWithoutList, id);
    }

    public StockTransfer mapWithoutList(ResultSet rs) throws DaoException {
        StockTransfer model = new StockTransfer();
        try {
            model.setId(rs.getInt(ID));
            model.setStockFrom(daoFactory.stockDao().getDataById(rs.getInt(STOCK_FROM)));
            model.setStockTo(daoFactory.stockDao().getDataById(rs.getInt(STOCK_TO)));
            model.setDate(LocalDate.parse(rs.getString(TRANSFER_DATE)));
//            model.setTransferListItems(daoFactory.stockTransferListDao().loadAllById(model.getId()));
            // use list not here
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }

}
