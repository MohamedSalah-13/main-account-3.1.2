package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Audit_log;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record AuditLogService(DaoFactory daoFactory) {

    public List<Audit_log> getProcessesData(LocalDate startDate, LocalDate endDate) throws DaoException {
        return daoFactory.processesDao().getAuditLogsBetweenDates(startDate, endDate);
    }

    public int deleteInRangeId(Integer... integer) throws DaoException {
        return daoFactory.processesDao().deleteRangeIds(integer);
    }
}
