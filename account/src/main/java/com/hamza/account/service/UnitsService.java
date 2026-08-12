package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteOutcome;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record UnitsService(DaoFactory daoFactory) {

    public List<UnitsModel> getUnitsModelList() throws DaoException {
        return daoFactory.unitsDao().loadAll();
    }

    public List<String> getUnitsModelNames() throws DaoException {
        return getUnitsModelList()
                .stream()
                .map(UnitsModel::getUnit_name)
                .toList();
    }

    public UnitsModel getUnitsByName(String name) throws DaoException {
        return daoFactory.unitsDao().getDataByString(name);
    }

    public UnitsModel getUnitsById(int id) throws DaoException {
        return daoFactory.unitsDao().getDataById(id);
    }

    public int insert(String name, double value) throws DaoException {
        AuthorizationGuard.require(AppPermissions.UNITS_CREATE);
        return daoFactory.unitsDao().insert(new UnitsModel(0, name, value));

    }

    /**
     * Renaming is always allowed. It used to be refused for the two units the
     * database ships with, which meant a business that spells "كرتونة"
     * differently was stuck with the seed spelling forever - and a rename is
     * safe: invoice lines reference the unit by id and carry their own factor.
     */
    public int update(int id, String name, double value) throws DaoException {
        AuthorizationGuard.require(AppPermissions.UNITS_UPDATE);
        return daoFactory.unitsDao().update(new UnitsModel(id, name, value));
    }

    /**
     * Why this unit cannot be deleted - a protected id, a missing permission, or
     * the items and invoice lines still pointing at it, counted - or null when it
     * can. The screen asks before it offers to delete.
     * <p>
     * This replaced {@code isUnitInUse}, which answered a bare yes and left the
     * screen to write its own sentence. The rule knows what is in the way, so the
     * sentence names it.
     */
    public DeleteOutcome checkDelete(int id) throws DaoException {
        return DeletionService.shared().check(DeleteRegistry.UNITS, id);
    }

    public int delete(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.UNITS, id, daoFactory.unitsDao()::deleteById)
                .rowsOrThrow();
    }
}
