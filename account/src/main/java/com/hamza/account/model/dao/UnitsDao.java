package com.hamza.account.model.dao;

import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UnitsDao extends AbstractDao<UnitsModel> {

    /** The unit the schema falls back to: {@code type INT DEFAULT 1}. */
    public static final int DEFAULT_UNIT_ID = 1;
    private final String TABLE_NAME = "units";
    private final String UNIT_ID = "unit_id";
    private final String UNIT_NAME = "unit_name";
    private final String VALUE_D = "value_d";
    private final String USER_ID = "user_id";

    protected UnitsDao() {
        super();
    }

    @Override
    public List<UnitsModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(UnitsModel unitsModel) throws DaoException {
        Object[] objects = {unitsModel.getUnit_name(), unitsModel.getValue(), unitsModel.getUsers().getId()};
        String insert = SqlStatements.insertStatement(TABLE_NAME, UNIT_NAME, VALUE_D, USER_ID);
        return executeUpdate(insert, objects);
    }

    @Override
    public int update(UnitsModel unitsModel) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, UNIT_ID, UNIT_NAME, VALUE_D);
        return executeUpdate(update, getData(unitsModel));
    }

    /**
     * Removes the row and nothing else.
     * <p>
     * What may be deleted is no longer decided here. The protected id and the
     * "is anything still using it" query used to live in this method - the only
     * DAO in the tree that checked either - and are now declared as
     * {@code DeleteRegistry.UNITS}, so units are refused for the same reasons, in
     * the same words, and through the same code as every other entity.
     */
    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid unit ID: " + id);
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, UNIT_ID);
        return executeUpdate(deleteStatement, id);
    }

    @Override
    public UnitsModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, UNIT_ID), this::map, id);
    }

    @Override
    public UnitsModel getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, UNIT_NAME), this::map, s);
    }

    @Override
    public Object[] getData(UnitsModel unitsModel) throws DaoException {
        return new Object[]{unitsModel.getUnit_name(), unitsModel.getValue(), unitsModel.getUnit_id()};
    }

    @Override
    public UnitsModel map(ResultSet rs) throws DaoException {
        UnitsModel unitsModel = new UnitsModel();
        try {
            unitsModel.setUnit_id(rs.getInt(UNIT_ID));
            unitsModel.setUnit_name(rs.getString(UNIT_NAME));
            unitsModel.setValue(rs.getDouble(VALUE_D));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return unitsModel;
    }
}
