package com.hamza.account.features.treasury.statement;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface TreasuryStatementRepository {
    List<TreasuryStatementRow> search(TreasuryStatementFilter filter) throws DaoException;
    TreasuryStatementSummary summarize(TreasuryStatementFilter filter) throws DaoException;
    TreasuryStatementOptions options() throws DaoException;
}
