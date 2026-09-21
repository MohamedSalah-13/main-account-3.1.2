package com.hamza.account.features.delegate.trend;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** Where one delegate's days are read from. */
public interface DelegateTrendRepository {

    /** One row per day with movement, oldest first, both days included. */
    List<DelegateTrendDay> daily(int delegateId, LocalDate from, LocalDate to) throws DaoException;
}
