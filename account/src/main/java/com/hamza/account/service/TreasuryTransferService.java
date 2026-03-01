package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryTransferModel;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record TreasuryTransferService(DaoFactory daoFactory) {

    public List<TreasuryTransferModel> getTreasuryTransferModelList() throws DaoException {
        return daoFactory.treasuryTransferDao().loadAll().stream().toList();
    }

    public TreasuryTransferModel getTreasuryTransferById(int id) throws DaoException {
        return daoFactory.treasuryTransferDao().getDataById(id);
    }

    public int insert(double amount, LocalDate date, String notes, int treasuryFrom, int treasuryTo) throws DaoException {
        return daoFactory.treasuryTransferDao().insert(new TreasuryTransferModel(0, amount, date, notes, treasuryFrom, treasuryTo));
    }

    public int update(int id, double amount, LocalDate date, String notes, int treasuryFrom, int treasuryTo) throws DaoException {
        return daoFactory.treasuryTransferDao().update(new TreasuryTransferModel(id, amount, date, notes, treasuryFrom, treasuryTo));
    }

    public int delete(int id) throws DaoException {
        return daoFactory.treasuryTransferDao().deleteById(id);
    }
}
