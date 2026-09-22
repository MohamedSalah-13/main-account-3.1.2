package com.hamza.account.features.capital;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public interface CapitalRepository {

    List<CapitalDay> days(LocalDate from, LocalDate to) throws DaoException;

    CapitalBefore before(LocalDate day) throws DaoException;

    BroughtForward broughtForward() throws DaoException;

    /** What the business holds and owes as recorded today - {@link CapitalStatements#RECONCILIATION}. */
    ReconciliationFigures reconciliation() throws DaoException;
}
