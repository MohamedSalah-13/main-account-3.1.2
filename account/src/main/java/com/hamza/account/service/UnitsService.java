package com.hamza.account.service;

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
        return daoFactory.unitsDao().insert(new UnitsModel(0, name, value));

    }

    /**
     * Renaming is always allowed. It used to be refused for the two units the
     * database ships with, which meant a business that spells "كرتونة"
     * differently was stuck with the seed spelling forever - and a rename is
     * safe: invoice lines reference the unit by id and carry their own factor.
     */
    public int update(int id, String name, double value) throws DaoException {
        return daoFactory.unitsDao().update(new UnitsModel(id, name, value));
    }

    /**
     * Whether anything still points at this unit - an item's base unit, one of
     * an item's units, or a line on an invoice already saved. Deleting one that
     * is referenced would fail on a foreign key, or orphan the history.
     */
    public boolean isUnitInUse(int id) throws DaoException {
        return daoFactory.unitsDao().isInUse(id);
    }

    public int delete(int id) throws DaoException {
        return daoFactory.unitsDao().deleteById(id);
    }
}
