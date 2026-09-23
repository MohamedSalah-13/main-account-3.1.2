package com.hamza.account.features.treasury.statement;

import com.hamza.account.features.currency.Currency;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface TreasuryStatementRepository {
    List<TreasuryStatementRow> search(TreasuryStatementFilter filter) throws DaoException;
    TreasuryStatementTotals summarize(TreasuryStatementFilter filter) throws DaoException;
    TreasuryStatementOptions options() throws DaoException;

    /** The currency a treasury is in, or {@code null} for the base. */
    Currency currencyOf(int treasuryId) throws DaoException;
}
