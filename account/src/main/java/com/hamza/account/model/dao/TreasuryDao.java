package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TreasuryDao extends AbstractDao<TreasuryModel> {

    public static final String COLUMN_NAME = "t_name";
    private final String TABLE_NAME = "treasury";
    private final String ID = "id";
    private final String AMOUNT = "amount";
    private final String USER_ID = "user_id";

    protected TreasuryDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TreasuryModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(TreasuryModel treasuryModel) throws DaoException {
        Object[] objects = {treasuryModel.getName(), treasuryModel.getFirstBalance(), treasuryModel.getUsers().getId()};
        String insert = SqlStatements.insertStatement(TABLE_NAME, COLUMN_NAME, AMOUNT, USER_ID);
        return executeUpdate(insert, objects);
    }

    @Override
    public int update(TreasuryModel unitsModel) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, ID, COLUMN_NAME, AMOUNT);
        return executeUpdate(update, getData(unitsModel));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(deleteStatement, id);
    }

    @Override
    public TreasuryModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public TreasuryModel getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, COLUMN_NAME), this::map, s);
    }

    @Override
    public Object[] getData(TreasuryModel model) throws DaoException {
        return new Object[]{model.getName(), model.getFirstBalance(), model.getId()};
    }

    @Override
    public TreasuryModel map(ResultSet rs) throws DaoException {
        TreasuryModel unitsModel = new TreasuryModel();
        try {
            double firstBalance = rs.getDouble(AMOUNT);
            unitsModel.setId(rs.getInt(ID));
            unitsModel.setName(rs.getString(COLUMN_NAME));
            unitsModel.setFirstBalance(firstBalance);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return unitsModel;
    }
}
