package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

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

    public int update(int id, String name, double value) throws DaoException {
        if (id == 1 || id == 2) throw new DaoException(Error_Text_Show.CAN_NOT_UPDATE);
        return daoFactory.unitsDao().update(new UnitsModel(id, name, value));
    }

    public int delete(int id) throws DaoException {
//        if (id == 1 || id == 2) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return daoFactory.unitsDao().deleteById(id);
    }
}
