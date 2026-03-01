package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record TreasuryBalanceService(DaoFactory daoFactory) {

    public List<TreasuryBalance> getAllTreasuryBalanceBetweenTwoDate(String fromDate, String toDate) throws DaoException {
        return daoFactory.treasuryBalanceDao().loadAllBetweenTwoData(fromDate, toDate);
    }

    public List<TreasuryBalance> getTreasuryBalanceSummary() throws DaoException {
        return daoFactory.treasuryBalanceDao().getSumTreasuryBalance();
    }

}
