package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.ExpensesDao;
import com.hamza.account.model.domain.Expenses;
import com.hamza.controlsfx.database.DaoException;

public record ExpensesService(DaoFactory daoFactory) {

    private ExpensesDao getDao() {
        return daoFactory.expensesDao();
    }

    public Expenses fetchExpenseById(int id) throws DaoException {
        return getDao().getDataById(id);
    }

}