package com.hamza.account.features.party.ageing;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** Where the ageing report's rows come from. An interface so the service can be tested without one. */
public interface PartyAgeingRepository {

    List<PartyAgeingRow> search(PartyAgeingFilter filter) throws DaoException;

    PartyAgeingSummary summarize(PartyAgeingFilter filter) throws DaoException;
}
