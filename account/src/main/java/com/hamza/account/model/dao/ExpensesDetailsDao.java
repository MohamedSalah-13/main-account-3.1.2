package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Expenses;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ExpensesDetailsDao extends AbstractDao<ExpensesDetails> {

    private final String TABLE_VIEW = "expenses_details_view";
    private final String TABLE_NAME = "expenses_details";
    private final String TYPE_CODE = "type_code";
    private final String DATE = "date";
    private final String AMOUNT = "amount";
    private final String NOTES = "notes";
    private final String EMP_ID = "emp_id";
    private final String ID = "id";
    private final String EMPLOYEE_NAME = "column_name";
    private final String EXPENSES_NAME = "expenses_name";
    private final String TREASURY_ID = "treasury_id";
    private final String USER_ID = "user_id";

    public ExpensesDetailsDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<ExpensesDetails> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public int insert(ExpensesDetails expensesDetails) throws DaoException {
        String query = SqlStatements.insertStatement(TABLE_NAME, TYPE_CODE
                , DATE, AMOUNT, NOTES
                , EMP_ID, TREASURY_ID, USER_ID);
        return executeUpdate(query, getData(expensesDetails));
    }

    @Override
    public int update(ExpensesDetails expensesDetails) throws DaoException {
        Object[] object = new Object[]{expensesDetails.getExpenses().getId()
                , expensesDetails.getLocalDate().toString()
                , expensesDetails.getAmount()
                , expensesDetails.getNotes()
                , expensesDetails.getEmployees().getId()
                , expensesDetails.getTreasuryModel().getId()
                , expensesDetails.getId()

        };
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID
                , TYPE_CODE, DATE, AMOUNT
                , NOTES, EMP_ID, TREASURY_ID), object);
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public ExpensesDetails getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_VIEW, ID), this::map, id);
    }

    @Override
    public Object[] getData(ExpensesDetails expensesDetails) throws DaoException {
        return new Object[]{expensesDetails.getExpenses().getId(), expensesDetails.getLocalDate().toString()
                , expensesDetails.getAmount(), expensesDetails.getNotes()
                , expensesDetails.getEmployees().getId(), expensesDetails.getTreasuryModel().getId()
                , expensesDetails.getUsers().getId()};
    }

    @Override
    public ExpensesDetails map(ResultSet rs) throws DaoException {
        ExpensesDetails expensesDetails = new ExpensesDetails();
        try {
            expensesDetails.setId(rs.getInt(ID));
            expensesDetails.setLocalDate(LocalDate.parse(rs.getString(DATE)));
            expensesDetails.setAmount(rs.getDouble(AMOUNT));
            expensesDetails.setNotes(rs.getString(NOTES));

            expensesDetails.setTreasuryModel(new TreasuryModel(rs.getInt(TREASURY_ID)));
            expensesDetails.setExpenses(new Expenses(rs.getInt(TYPE_CODE), rs.getString(EXPENSES_NAME)));
            String string = rs.getString(EMPLOYEE_NAME);
            expensesDetails.setEmployees(new Employees(rs.getInt(EMP_ID), string));

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return expensesDetails;
    }

}
