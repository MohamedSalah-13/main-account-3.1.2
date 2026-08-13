package com.hamza.account.model.dao;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ItemBarcodesDao extends AbstractDao<String> {

    private static final String TABLE_NAME = "item_barcodes";
    private static final String ITEM_ID = "item_id";
    private static final String BARCODE = "barcode";
    private static final String INSERT = SqlStatements.insertStatement(TABLE_NAME, ITEM_ID, BARCODE);

    public ItemBarcodesDao() {
        super();
    }

    public List<String> getBarcodesByItemId(int itemId) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ITEM_ID);
        return queryForObjects(query, this::map, itemId);
    }

    public int insertBarcodesForItem(int itemId, List<String> barcodes) throws DaoException {
        if (barcodes == null || barcodes.isEmpty()) return 0;

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (String barcode : barcodes) {
                    statement.setObject(1, itemId);
                    statement.setObject(2, barcode);
                    statement.addBatch();
                }
                int[] results = statement.executeBatch();
                return results.length;
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                    throw new BusinessRuleException(Error_Text_Show.DUPLICATE_ENTRY, e);
                }
                throw new DaoException(e);
            }
        });
    }

    public int deleteByItemId(int itemId) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ITEM_ID), itemId);
    }

    public String map(ResultSet rs) throws DaoException {
        try {
            return rs.getString(BARCODE);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }


}
