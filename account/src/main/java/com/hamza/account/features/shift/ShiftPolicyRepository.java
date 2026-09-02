package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface ShiftPolicyRepository {
    ShiftPolicy load() throws DaoException;

    List<TreasuryShiftPolicy> loadTreasuries() throws DaoException;

    ShiftTrackingMode trackingMode(int treasuryId) throws DaoException;

    boolean hasOpenShifts() throws DaoException;

    void save(ShiftPolicy policy) throws DaoException;

    void saveTreasury(TreasuryShiftPolicy policy) throws DaoException;
}
