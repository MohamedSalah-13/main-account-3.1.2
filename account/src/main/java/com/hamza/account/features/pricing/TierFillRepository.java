package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.List;

/** Where a fill reads the catalogue's prices and writes a tier's - a seam, so the service is tested alone. */
public interface TierFillRepository {

    /** Every item with its cost, its three prices and its other units' own figures. */
    List<TierFill.ItemSource> readAll() throws DaoException;

    /** Locks the items and their units, in the order every price writer takes them. */
    void lockCatalogue() throws DaoException;

    /** Writes one item's price on a tier, only if it still holds {@code before}. Answers the rows written. */
    int writeItem(int itemId, int tierId, BigDecimal before, BigDecimal after) throws DaoException;

    /** Writes one unit's own price on a tier, only if it still holds {@code before}. */
    int writeUnit(int itemId, int unitId, int tierId, BigDecimal before, BigDecimal after) throws DaoException;
}
