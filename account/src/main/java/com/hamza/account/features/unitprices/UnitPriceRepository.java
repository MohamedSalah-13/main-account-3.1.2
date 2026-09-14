package com.hamza.account.features.unitprices;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Where the unit prices screen reads items with their units, and writes their prices. */
public interface UnitPriceRepository {

    List<UnitPriceItem> findPage(UnitPriceFilter filter, int limit, int offset) throws DaoException;

    int count(UnitPriceFilter filter) throws DaoException;

    /** Every item the filter matches, with its units. */
    List<UnitPriceItem> findAll(UnitPriceFilter filter) throws DaoException;

    /**
     * These items and their units, as stored now, with their rows locked until the transaction
     * ends. An id with no item is simply absent from the answer.
     */
    Map<Integer, UnitPriceItem> lockItems(Set<Integer> itemIds) throws DaoException;

    int updateItemPrices(int itemId, Prices prices, int userId) throws DaoException;

    int updateUnitPrices(int itemId, int unitId, Prices prices, int userId) throws DaoException;
}
