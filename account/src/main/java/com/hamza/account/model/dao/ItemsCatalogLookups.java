package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;

import java.util.HashMap;
import java.util.Map;

/**
 * The three small tables an item row needs a name out of - its sub group (and through
 * it the main group), its base unit, and its warehouse - read once for a whole page
 * instead of once per row.
 * <p>
 * {@code ItemsDao.map} resolves each of these with {@code getDataById} while the item
 * result set is still open, so a page of fifty items costs several hundred round trips
 * and every one of them is paid on whichever thread asked for the page. These three
 * tables are lookup data: a handful of groups, a handful of units, a handful of
 * warehouses, all of it small enough to hold whole. Loading them costs four queries
 * regardless of how many items the page carries.
 * <p>
 * An instance is a <em>snapshot</em>, and deliberately short-lived: build one per query
 * and let it go with the list it mapped. It is not a cache with an invalidation problem -
 * renaming a group while a page is being mapped shows the old name on that page and the
 * new one on the next, which is exactly what the per-row lookups did between rows.
 * <p>
 * An id with no row maps to {@code null}, the same answer {@code getDataById} gives for a
 * row that is not there.
 */
final class ItemsCatalogLookups {

    private final Map<Integer, SubGroups> subGroups;
    private final Map<Integer, UnitsModel> units;
    private final Map<Integer, Stock> stocks;

    private ItemsCatalogLookups(Map<Integer, SubGroups> subGroups,
                                Map<Integer, UnitsModel> units,
                                Map<Integer, Stock> stocks) {
        this.subGroups = subGroups;
        this.units = units;
        this.stocks = stocks;
    }

    static ItemsCatalogLookups load(DaoFactory daoFactory) throws DaoException {
        Map<Integer, SubGroups> subGroups = new HashMap<>();
        for (SubGroups group : daoFactory.getSupGroupsDao().loadAllResolved()) {
            subGroups.put(group.getId(), group);
        }
        Map<Integer, UnitsModel> units = new HashMap<>();
        for (UnitsModel unit : daoFactory.unitsDao().loadAll()) {
            units.put(unit.getUnit_id(), unit);
        }
        Map<Integer, Stock> stocks = new HashMap<>();
        for (Stock stock : daoFactory.stockDao().loadAll()) {
            stocks.put(stock.getId(), stock);
        }
        return new ItemsCatalogLookups(subGroups, units, stocks);
    }

    SubGroups subGroup(int id) {
        return subGroups.get(id);
    }

    UnitsModel unit(int id) {
        return units.get(id);
    }

    Stock stock(int id) {
        return stocks.get(id);
    }
}
