package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryTransfer;
import com.hamza.controlsfx.database.DaoException;

public record TreasuryTransferService(DaoFactory daoFactory) {


    public TreasuryTransfer getTreasuryTransferById(int id) throws DaoException {
        return daoFactory.treasuryTransferDao().getDataById(id);
    }


    public int delete(int id) throws DaoException {
        return daoFactory.treasuryTransferDao().deleteById(id);
    }
}
