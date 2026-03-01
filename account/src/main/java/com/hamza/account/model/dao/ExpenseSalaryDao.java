package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ExpensesSalary;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.SQLException;

public class ExpenseSalaryDao extends AbstractDao<ExpensesSalary> {

    private final String TABLE_NAME = "expense_salary";
    private final String ID = "id";
    private final String EMPLOYEE_ID = "employee_id";
    private final String EXPENSES_DETAILS_ID = "expenses_details_id";

    public ExpenseSalaryDao(Connection connection) {
        super(connection);
    }

    @Override
    public int insert(ExpensesSalary expensesSalary) throws DaoException {
        try {
            String querySalary = SqlStatements.insertStatement(TABLE_NAME, EMPLOYEE_ID, EXPENSES_DETAILS_ID);
            return executeUpdateWithException(querySalary, expensesSalary.getEmployee_id(), expensesSalary.getExpense_details_id());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(ExpensesSalary expensesSalary) throws DaoException {
        try {
            String querySalary = SqlStatements.insertStatement(TABLE_NAME, ID, EMPLOYEE_ID, EXPENSES_DETAILS_ID);
            return executeUpdateWithException(querySalary, expensesSalary.getEmployee_id(), expensesSalary.getExpense_details_id(), expensesSalary.getId());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

}
