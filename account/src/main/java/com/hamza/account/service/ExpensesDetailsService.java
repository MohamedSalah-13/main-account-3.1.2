package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.ExpensesDetailsDao;
import com.hamza.account.model.domain.ExpensesDetails;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record ExpensesDetailsService(DaoFactory daoFactory) {

    private ExpensesDetailsDao expensesDetailsDao() {
        return daoFactory.expensesDetailsDao();
    }

    public List<ExpensesDetails> fetchAllExpensesDetailsList() throws DaoException {
        return expensesDetailsDao().loadAll();
    }

    public ExpensesDetails getExpensesDetailsById(int id) throws DaoException {
        return expensesDetailsDao().getDataById(id);
    }

    public int deleteById(int id) throws DaoException {
        return daoFactory.expensesDetailsDao().deleteById(id);
    }

    public int insert(ExpensesDetails expensesDetails) throws DaoException {
        return daoFactory.expensesDetailsDao().insert(expensesDetails);
    }

    public int update(ExpensesDetails expensesDetails) throws DaoException {
        return daoFactory.expensesDetailsDao().update(expensesDetails);
    }
}