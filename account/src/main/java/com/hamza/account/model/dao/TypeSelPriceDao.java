package com.hamza.account.model.dao;

import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TypeSelPriceDao extends AbstractDao<SelPriceTypeModel> {

    public static final String NAME = "name";
    private final String ID = "id";
    private final String TABLE_NAME = "type_price";
    private final String USER_ID = "user_id";


    TypeSelPriceDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<SelPriceTypeModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int update(SelPriceTypeModel selPriceTypeModel) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, NAME), selPriceTypeModel.getName(), selPriceTypeModel.getId());
    }

    @Override
    public SelPriceTypeModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public SelPriceTypeModel getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, NAME), this::map, s);
    }

    @Override
    public Object[] getData(SelPriceTypeModel itemsModel) {
        return new Object[]{itemsModel.getName()};
    }

    @Override
    public SelPriceTypeModel map(ResultSet rs) throws DaoException {
        SelPriceTypeModel itemsModel = new SelPriceTypeModel();
        try {
            itemsModel.setId(rs.getInt(ID));
            itemsModel.setName(rs.getString(NAME));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return itemsModel;
    }

}
