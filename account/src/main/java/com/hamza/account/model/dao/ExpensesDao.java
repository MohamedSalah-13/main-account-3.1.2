package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Expenses;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ExpensesDao extends AbstractDao<Expenses> {

    private final String TABLE_NAME = "expenses";
    private final String EXPENSES_NAME = "expenses_name";
    private final String ID = "id";

    public ExpensesDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Expenses> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Expenses expensesDetails) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, EXPENSES_NAME);
        return executeUpdate(query, getData(expensesDetails));

    }

    @Override
    public int update(Expenses expensesDetails) throws DaoException {
        Object[] object = new Object[]{expensesDetails.getName(), expensesDetails.getId()};
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, EXPENSES_NAME), object);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Expenses getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Expenses getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, EXPENSES_NAME), this::map, s);
    }

    @Override
    public Object[] getData(Expenses expensesDetails) throws DaoException {
        return new Object[]{expensesDetails.getName()};
    }

    @Override
    public Expenses map(ResultSet rs) throws DaoException {
        Expenses expensesDetails = new Expenses();
        try {
            expensesDetails.setId(rs.getInt(ID));
            expensesDetails.setName(rs.getString(EXPENSES_NAME));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return expensesDetails;
    }

}