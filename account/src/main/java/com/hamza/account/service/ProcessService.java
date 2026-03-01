package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Processes_Data;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record ProcessService(DaoFactory daoFactory) {

    public List<Processes_Data> getProcessesData() throws DaoException {
        return daoFactory.processesDao().loadAll();
    }

    public int deleteInRangeId(Integer... integer) throws DaoException {
        return daoFactory.processesDao().deleteRangeIds(integer);
    }
}
