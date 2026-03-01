package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.util.List;

public record TreasuryService(DaoFactory daoFactory) {

    public List<TreasuryModel> getTreasuryModelList() throws DaoException {
        return daoFactory.treasuryDao().loadAll();
    }

    public List<String> listTreasuryModelNames() throws DaoException {
        return getTreasuryModelList()
                .stream()
                .map(TreasuryModel::getName)
                .toList();
    }

    public TreasuryModel getTreasuryByName(String name) throws DaoException {
        return daoFactory.treasuryDao().getDataByString(name);
    }

    public TreasuryModel getTreasuryById(int id) throws DaoException {
        return daoFactory.treasuryDao().getDataById(id);
    }

    public int delete(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.treasuryDao().deleteById(id);
    }
}
