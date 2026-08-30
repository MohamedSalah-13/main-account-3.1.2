package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record TreasuryBalanceService(DaoFactory daoFactory) {

    public List<TreasuryBalance> getAllTreasuryBalanceBetweenTwoDate(String fromDate, String toDate) throws DaoException {
        return daoFactory.treasuryBalanceDao().loadAllBetweenTwoData(fromDate, toDate);
    }

    /**
     * What each treasury holds now: opening balance, everything in, everything out.
     * <p>
     * This replaced a {@code SUM} over {@code treasury_balance} taken in Java, which
     * left out the opening balance and both halves of every transfer - so a treasury
     * that had been topped up from another one reported the wrong figure on the
     * dashboard while the sum across all treasuries still looked right.
     */
    public List<TreasuryBalanceSummary> getTreasuryBalanceSummary() throws DaoException {
        return daoFactory.treasuryCurrentBalanceDao().loadAll();
    }

    /** Only the treasuries still in use - see {@code treasury.is_active}. */
    public List<TreasuryBalanceSummary> getActiveTreasuryBalances() throws DaoException {
        return daoFactory.treasuryCurrentBalanceDao().loadActive();
    }
}
