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

    /**
     * The headings an expense can be filed under - the six V1 seeds plus whatever the shop added.
     * <p>
     * A picker reads this rather than naming an id: {@code expenses.id} is not auto-increment and
     * the names are editable, so a constant like {@code SALARY -> 1} is the {@code UsersType}
     * mistake at a different table. The employee payment screen is the first caller.
     */
    public java.util.List<Expenses> headings() throws DaoException {
        return getDao().loadAll();
    }

}