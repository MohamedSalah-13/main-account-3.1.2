package com.hamza.account.features.party.payment;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface PartyPaymentsRepository {

    /** A treasury's id and name, for the report's filter. */
    record TreasuryOption(int id, String name) {
    }

    List<PartyPaymentRow> page(PartyPaymentsFilter filter) throws DaoException;

    List<TreasuryOption> treasuries() throws DaoException;
}
