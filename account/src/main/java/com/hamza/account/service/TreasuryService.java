package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record TreasuryService(DaoFactory daoFactory) {

    public List<Treasury> getTreasuryModelList() throws DaoException {
        return daoFactory.treasuryDao().loadAll();
    }

    public List<String> listTreasuryModelNames() throws DaoException {
        return getTreasuryModelList()
                .stream()
                .map(Treasury::getName)
                .toList();
    }

    public Treasury getTreasuryByName(String name) throws DaoException {
        return daoFactory.treasuryDao().getDataByString(name);
    }

    public Treasury getTreasuryById(int id) throws DaoException {
        return daoFactory.treasuryDao().getDataById(id);
    }

    public int insert(Treasury treasury) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_UPDATE);
        return daoFactory.treasuryDao().insert(treasury);
    }

    public int update(Treasury treasury) throws DaoException {
        AuthorizationGuard.require(AppPermissions.TREASURY_UPDATE);
        return daoFactory.treasuryDao().update(treasury);
    }

    public int delete(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.TREASURIES, id, daoFactory.treasuryDao()::deleteById)
                .rowsOrThrow();
    }
}
