package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Earnings;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record EarningsService(DaoFactory daoFactory) {

    public List<Earnings> getEarningsByDateRange(LocalDate startDate, LocalDate endDate) throws DaoException {
        return daoFactory.earningsDao().getEarningsByDateRange(startDate, endDate);
    }

}
