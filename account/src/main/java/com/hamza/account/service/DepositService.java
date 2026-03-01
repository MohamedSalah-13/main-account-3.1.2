package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.DepositDao;
import com.hamza.account.model.domain.AddDeposit;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record DepositService(DaoFactory daoFactory) {

    public List<AddDeposit> getAllDeposits() throws DaoException {
        return daoFactory.depositDao().loadAll();
    }

    public int insertDeposit(AddDeposit deposit) throws DaoException {
        return getDepositDao().insert(deposit);
    }

    public int updateDeposit(AddDeposit deposit) throws DaoException {
        return getDepositDao().update(deposit);
    }

    public int deleteDeposit(int id) throws DaoException {
        return getDepositDao().deleteById(id);
    }

    @NotNull
    private DepositDao getDepositDao() {
        return daoFactory.depositDao();
    }

}
