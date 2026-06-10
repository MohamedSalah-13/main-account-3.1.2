package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.AreaDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;

import java.util.List;

public record AreaService(DaoFactory daoFactory) {


    public List<Area> fetchAllAreas() throws DaoException {
        return getAreaDao().loadAll();
    }

    private AreaDao getAreaDao() {
        return daoFactory.areaDao();
    }
}
