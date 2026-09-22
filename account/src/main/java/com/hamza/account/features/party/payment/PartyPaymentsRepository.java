package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public interface PartyPaymentsRepository {

    List<PartyPaymentRow> between(PartyKind kind, LocalDate from, LocalDate to) throws DaoException;
}
