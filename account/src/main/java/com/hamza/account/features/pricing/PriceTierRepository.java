package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Map;

/** Where the tiers are read and written - a seam, so {@link PriceTierService} is tested without MySQL. */
public interface PriceTierRepository {

    /** The three tiers, by id. */
    List<PriceTier> all() throws DaoException;

    /** Locks the tier rows for a save - the same order every writer takes them in. */
    void lockAll() throws DaoException;

    /** Writes one tier's name, state and rule. Answers the rows written. */
    int update(PriceTier tier) throws DaoException;

    /**
     * Moves the names of these tiers out of the way first, so a save that swaps two names does not
     * meet the {@code UNIQUE (name)} index V1 put on the table half way through.
     */
    void clearNames(List<Integer> tierIds) throws DaoException;

    /** How many active customers are on each tier - what switching one off would move to tier 1. */
    Map<Integer, Integer> activeCustomersByTier() throws DaoException;
}
