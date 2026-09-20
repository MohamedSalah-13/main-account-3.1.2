package com.hamza.account.model.dao;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.controlsfx.database.DaoException;

import java.util.HashMap;
import java.util.Map;

/**
 * The two small tables a party row needs a name out of - its area, and for a customer the
 * price tier it buys at - read once for a whole query instead of once per row.
 * <p>
 * This is {@link ItemsCatalogLookups} applied to the other half of the master data, and it
 * was written for the same defect: {@code CustomerDao.map} resolved the price tier with a
 * {@code getDataById} of its own while the customer result set was still open, so reading
 * the customer list cost one query plus one per row. Measured against a copy of a real
 * database - 147 parties, warmed, on localhost - that is <b>101 ms a read</b>, and the
 * invoice screen was doing two of them on the JavaFX thread every time a cashier named a
 * customer. The screen no longer reads the list at all; what is left is that the read
 * itself was quadratic in a table of three rows. {@code type_price} holds <em>three</em>
 * rows on that database, and {@code table_area} a handful.
 * <p>
 * An instance is a <em>snapshot</em> and deliberately short-lived: build one per query and
 * let it go with the list it mapped. It is not a cache with an invalidation problem -
 * renaming an area while a page is being mapped shows the old name on that page and the
 * new one on the next, which is exactly what the per-row lookups did between rows.
 * <p>
 * <b>It also replaced the area join.</b> Both statements carried one only so {@code map}
 * could read {@code area_name}; nothing filters or orders by it. The supplier's was an
 * <em>inner</em> join, so a supplier whose area row had been deleted dropped out of
 * {@code loadAll} entirely - the defect the customer's join was changed from inner to
 * {@code LEFT} to fix, left standing on the other side because the supplier's *searches*
 * never joined at all and that was mistaken for the whole story.
 */
final class PartyLookups {

    private final Map<Integer, String> areaNames;
    private final Map<Integer, SelPriceTypeModel> priceTiers;

    private PartyLookups(Map<Integer, String> areaNames, Map<Integer, SelPriceTypeModel> priceTiers) {
        this.areaNames = areaNames;
        this.priceTiers = priceTiers;
    }

    /**
     * Reads what the given kind of party needs, and nothing else: a supplier has no price
     * tier, so loading the tiers for one would be a query asked for nobody.
     */
    static PartyLookups load(DaoFactory daoFactory, PartyKind kind) throws DaoException {
        Map<Integer, String> areaNames = new HashMap<>();
        for (Area area : daoFactory.areaDao().loadAll()) {
            if (area != null) {
                areaNames.put(area.getId(), area.getArea_name());
            }
        }
        Map<Integer, SelPriceTypeModel> priceTiers = new HashMap<>();
        if (kind == PartyKind.CUSTOMER) {
            for (SelPriceTypeModel tier : daoFactory.getItemsSelPriceDao().loadAll()) {
                if (tier != null) {
                    priceTiers.put(tier.getId(), tier);
                }
            }
        }
        return new PartyLookups(areaNames, priceTiers);
    }

    /**
     * The party's area, never {@code null}.
     * <p>
     * An area row that is gone leaves the id and an empty name, which is what the customer
     * mapper produced from its {@code LEFT} join and reads on screen as "no area". The
     * supplier mapper used to answer {@code null} here - {@code getDataById} on a missing
     * row - and hand a party with no area object to every caller.
     */
    Area area(int id) {
        String name = areaNames.get(id);
        return new Area(id, name == null ? "" : name);
    }

    /**
     * The price tier the customer buys at, or {@code null} for an id with no row - the same
     * answer {@code getDataById} gave, so nothing downstream changes.
     */
    SelPriceTypeModel priceTier(int id) {
        return priceTiers.get(id);
    }
}
