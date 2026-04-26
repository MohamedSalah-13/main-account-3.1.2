package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Audit_log;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record AuditLogService(DaoFactory daoFactory) {

    public List<Audit_log> getProcessesData() throws DaoException {
        return daoFactory.processesDao().loadAll();
    }

    public int deleteInRangeId(Integer... integer) throws DaoException {
        return daoFactory.processesDao().deleteRangeIds(integer);
    }
}
