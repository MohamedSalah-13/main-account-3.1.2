package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** Where a trend's days come from. A seam so {@link PartyTrendService} is tested without MySQL. */
public interface PartyTrendRepository {

    /**
     * Each day with movement between two days, both included, summed over every party of the
     * kind - or over one, when {@code partyId} is not null.
     */
    List<PartyTrendDay> daily(PartyKind kind, LocalDate from, LocalDate to, Integer partyId)
            throws DaoException;
}
