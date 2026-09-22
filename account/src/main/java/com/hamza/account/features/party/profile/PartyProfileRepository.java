package com.hamza.account.features.party.profile;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PartyProfileRepository {

    List<PartyItemRow> items(PartyKind kind, int partyId, LocalDate from, LocalDate to) throws DaoException;

    List<PartyProfileDay> days(PartyKind kind, int partyId, LocalDate from, LocalDate to) throws DaoException;

    Optional<LocalDate> lastDocument(PartyKind kind, int partyId) throws DaoException;
}
