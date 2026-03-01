package com.hamza.account.service;

import com.hamza.account.model.dao.AreaDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Error_Text_Show;

import java.util.List;

public record AreaService(DaoFactory daoFactory) {


    public List<Area> fetchAllAreas() throws DaoException {
        return getAreaDao().loadAll();
    }

    public int deleteArea(int id) throws DaoException {
        if (id == 1) throw new DaoException(Error_Text_Show.CANT_DELETE);
        return getAreaDao().deleteById(id);
    }

    public int updateArea(Area area) throws DaoException {
        return getAreaDao().update(area);
    }

    public int insertArea(Area area) throws DaoException {
        return getAreaDao().insert(area);
    }

    private AreaDao getAreaDao() {
        return daoFactory.areaDao();
    }
}
