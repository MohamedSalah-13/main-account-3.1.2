package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsMiniQuantity;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ItemMiniDao extends AbstractDao<ItemsMiniQuantity> {

    private final String TABLE_NAME = "mini_quantity_view";
    private final String ID = "id";
    private final String NAME_ITEM = "nameItem";
    private final String MINI_QUANTITY = "mini_quantity";
    private final String BALANCE = "balance";

    public ItemMiniDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<ItemsMiniQuantity> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public ItemsMiniQuantity map(ResultSet rs) throws DaoException {
        try {
            return new ItemsMiniQuantity(rs.getInt(ID), rs.getString(NAME_ITEM), rs.getDouble(MINI_QUANTITY), rs.getDouble(BALANCE));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
