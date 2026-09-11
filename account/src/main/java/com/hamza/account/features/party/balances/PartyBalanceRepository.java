package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** The read side of the balances list. One implementation over JDBC; mocked in tests. */
public interface PartyBalanceRepository {

    List<PartyBalanceRow> search(PartyBalanceFilter filter) throws DaoException;

    PartyBalanceSummary summarize(PartyBalanceFilter filter) throws DaoException;

    List<PartyAreaOption> areas(PartyKind kind) throws DaoException;
}
