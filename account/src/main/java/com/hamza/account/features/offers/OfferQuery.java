package com.hamza.account.features.offers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every statement over the offers, in one place and pinned character for character by
 * {@code OfferQueryTest}. Only identifiers written here enter SQL; every value is bound.
 */
public final class OfferQuery {

    /** An SQL text and the values bound to it, in order. */
    public record Statement(String sql, List<Object> parameters) {

        public Statement {
            parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        }
    }

    static final String OFFER_COLUMNS = "o.id, o.name, o.kind, o.status, o.starts_on, o.ends_on, o.weekdays,"
            + " o.priority, o.percent, o.amount, o.offer_price, o.unit_id, o.buy_quantity, o.get_quantity,"
            + " o.get_percent, o.max_per_invoice, o.quantity_limit, o.notes, o.updated_at";

    /**
     * Every offer switched on, whatever its dates: the till's snapshot. The engine judges the dates against
     * the invoice's own, which the person at the till may move - and an active offer is a handful of rows.
     */
    public static final String ACTIVE_SQL = "SELECT " + OFFER_COLUMNS + " FROM offer o"
            + " WHERE o.status = 'ACTIVE' ORDER BY o.id";

    /** The offers in force on a document's date - what the save judges a sale by. */
    public static final String IN_FORCE_SQL = "SELECT " + OFFER_COLUMNS + " FROM offer o"
            + " WHERE o.status = 'ACTIVE' AND o.starts_on <= ? AND (o.ends_on IS NULL OR o.ends_on >= ?)"
            + " ORDER BY o.id";

    public static final String FIND_SQL = "SELECT " + OFFER_COLUMNS + " FROM offer o WHERE o.id = ?";

    /** The offers a saved sale's own lines carry - which reach it on an edit even once stopped (ق-ع٧). */
    public static final String DOCUMENT_OFFERS_SQL = "SELECT DISTINCT s.offer_id FROM sales s"
            + " WHERE s.invoice_number = ? AND s.offer_id IS NOT NULL";

    public static final String LOCK_SQL = "SELECT id, status, updated_at FROM offer WHERE id = ? FOR UPDATE";

    public static final String NAME_TAKEN_SQL = "SELECT COUNT(*) FROM offer WHERE name = ? AND id <> ?";

    public static final String INSERT_SQL = "INSERT INTO offer (name, kind, status, starts_on, ends_on, weekdays,"
            + " priority, percent, amount, offer_price, unit_id, buy_quantity, get_quantity, get_percent,"
            + " max_per_invoice, quantity_limit, notes, user_id)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** The version is compared here, so an edit made on another till since this one read the row is refused. */
    public static final String UPDATE_SQL = "UPDATE offer SET name = ?, kind = ?, starts_on = ?, ends_on = ?,"
            + " weekdays = ?, priority = ?, percent = ?, amount = ?, offer_price = ?, unit_id = ?,"
            + " buy_quantity = ?, get_quantity = ?, get_percent = ?, max_per_invoice = ?, quantity_limit = ?,"
            + " notes = ? WHERE id = ? AND updated_at = ?";

    public static final String STATUS_SQL = "UPDATE offer SET status = ? WHERE id = ? AND updated_at = ?";

    public static final String DELETE_SQL = "DELETE FROM offer WHERE id = ?";

    public static final String DELETE_TARGETS_SQL = "DELETE FROM offer_target WHERE offer_id = ?";

    public static final String INSERT_TARGET_SQL = "INSERT INTO offer_target (offer_id, role, scope, item_id,"
            + " unit_id, sub_group_id, main_group_id, excluded) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String DELETE_TIERS_SQL = "DELETE FROM offer_price_tier WHERE offer_id = ?";

    public static final String INSERT_TIER_SQL = "INSERT INTO offer_price_tier (offer_id, price_tier_id) VALUES (?, ?)";

    /**
     * What an offer has given, from the sale lines naming it, less the lines of the returns naming it: how
     * many invoices and lines, the discount given, the lines' net, and the first and last day it was used.
     * The last day is the floor its end may be brought back to (ق-ع٧).
     */
    public static final String USAGE_SQL = "SELECT COUNT(DISTINCT s.invoice_number) AS invoices,"
            + " COUNT(*) AS lines_count,"
            + " COALESCE(SUM(s.offer_discount), 0) AS given,"
            + " COALESCE(SUM(s.total_sel_price - s.discount), 0) AS net,"
            + " MIN(ts.invoice_date) AS first_used, MAX(ts.invoice_date) AS last_used,"
            + " (SELECT COALESCE(SUM(r.offer_discount), 0) FROM sales_re r WHERE r.offer_id = ?) AS returned,"
            + " COALESCE(SUM(s.offer_quantity), 0)"
            + " - (SELECT COALESCE(SUM(r.offer_quantity), 0) FROM sales_re r WHERE r.offer_id = ?) AS units"
            + " FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number"
            + " WHERE s.offer_id = ?";

    /**
     * The offers a sale is judged by for their global limit, locked in id order before their counts are read -
     * so a second till saving the same offer waits here until the first has committed (ق-ع١٠).
     */
    static String lockLimitedSql(int offers) {
        return "SELECT id FROM offer WHERE id IN (" + placeholders(offers) + ") ORDER BY id FOR UPDATE";
    }

    /**
     * The units each offer covered on every sale line but one document's - a locking read at the save, which
     * sees what another till committed while this one waited for the offer's lock, where a plain read under
     * REPEATABLE READ would see the snapshot it began with. The last value bound is the document left out.
     */
    static String usedOnSalesSql(int offers, boolean lock) {
        return "SELECT s.offer_id, COALESCE(SUM(s.offer_quantity), 0) FROM sales s"
                + " WHERE s.offer_id IN (" + placeholders(offers) + ") AND s.invoice_number <> ?"
                + " GROUP BY s.offer_id" + (lock ? " FOR SHARE" : "");
    }

    /**
     * The units the returns naming each offer gave back. A plain read even at the save: a return committed
     * meanwhile only leaves more of the limit than this sees, never less.
     */
    static String returnedSql(int offers) {
        return "SELECT r.offer_id, COALESCE(SUM(r.offer_quantity), 0) FROM sales_re r"
                + " WHERE r.offer_id IN (" + placeholders(offers) + ") GROUP BY r.offer_id";
    }

    /** Whether anything names the offer - a sale line or a return line. Once it does, its terms are history. */
    public static final String USED_SQL = "SELECT (SELECT COUNT(*) FROM sales WHERE offer_id = ?)"
            + " + (SELECT COUNT(*) FROM sales_re WHERE offer_id = ?)";

    public static final String SUB_GROUPS_SQL = "SELECT id, name FROM sub_group ORDER BY name";

    public static final String MAIN_GROUPS_SQL = "SELECT id, name_g FROM main_group ORDER BY name_g";

    public static final String UNITS_SQL = "SELECT unit_id, unit_name FROM units ORDER BY unit_id";

    /**
     * Every item in its base unit, for the below-cost check: its groups, its cost and its three prices. The
     * base unit is {@code items.unit_id} with a factor of one, never a row of {@code items_units}.
     */
    public static final String BASE_CANDIDATES_SQL = "SELECT i.id, i.nameItem, i.unit_id, un.unit_name,"
            + " i.sub_num, sg.main_id, 1 AS factor, i.buy_price AS cost,"
            + " i.sel_price1 AS price1, i.sel_price2 AS price2, i.sel_price3 AS price3"
            + " FROM items i JOIN sub_group sg ON sg.id = i.sub_num JOIN units un ON un.unit_id = i.unit_id";

    /**
     * Every item in one named unit, for an offer written for that unit: the items whose base it is, and the
     * items that sell in it - at the unit's own price and cost where it has one, else the item's times the
     * factor, which is what {@code ItemUnits} charges at the till.
     */
    public static final String UNIT_CANDIDATES_SQL = BASE_CANDIDATES_SQL + " WHERE i.unit_id = ?"
            + " UNION ALL"
            + " SELECT i.id, i.nameItem, iu.unit, un.unit_name, i.sub_num, sg.main_id, iu.quantity AS factor,"
            + " IF(iu.buy_price > 0, iu.buy_price, i.buy_price * iu.quantity) AS cost,"
            + " IF(iu.sel_price > 0, iu.sel_price, i.sel_price1 * iu.quantity) AS price1,"
            + " IF(iu.sel_price2 > 0, iu.sel_price2, i.sel_price2 * iu.quantity) AS price2,"
            + " IF(iu.sel_price3 > 0, iu.sel_price3, i.sel_price3 * iu.quantity) AS price3"
            + " FROM items_units iu JOIN items i ON i.id = iu.items_id"
            + " JOIN sub_group sg ON sg.id = i.sub_num JOIN units un ON un.unit_id = iu.unit"
            + " WHERE iu.unit = ? AND iu.unit <> i.unit_id";

    /** Each target of the offers named, with what it names spelled out for the screen. */
    static String targetsSql(int offers) {
        return "SELECT t.offer_id, t.role, t.scope, t.item_id, t.unit_id, t.sub_group_id, t.main_group_id, t.excluded,"
                + " i.nameItem, un.unit_name, sg.name AS sub_group_name, mg.name_g AS main_group_name"
                + " FROM offer_target t"
                + " LEFT JOIN items i ON i.id = t.item_id"
                + " LEFT JOIN units un ON un.unit_id = t.unit_id"
                + " LEFT JOIN sub_group sg ON sg.id = t.sub_group_id"
                + " LEFT JOIN main_group mg ON mg.id = t.main_group_id"
                + " WHERE t.offer_id IN (" + placeholders(offers) + ") ORDER BY t.offer_id, t.id";
    }

    static String tiersSql(int offers) {
        return "SELECT offer_id, price_tier_id FROM offer_price_tier WHERE offer_id IN (" + placeholders(offers)
                + ") ORDER BY offer_id, price_tier_id";
    }

    static String byIdsSql(int offers) {
        return "SELECT " + OFFER_COLUMNS + " FROM offer o WHERE o.id IN (" + placeholders(offers) + ") ORDER BY o.id";
    }

    /**
     * The offers screen's list: every offer the filter keeps, with its unit's name, how many targets it has,
     * how many sale lines name it and the discount they were given. Switched-on first, then drafts, then the
     * stopped; the newest start first within each.
     */
    public static Statement list(OfferFilter filter) {
        List<Object> values = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filter.text() != null) {
            where.append(" AND o.name LIKE ? ESCAPE '!'");
            values.add("%" + escape(filter.text()) + "%");
        }
        if (filter.status() != null) {
            where.append(" AND o.status = ?");
            values.add(filter.status().name());
        }
        if (filter.kind() != null) {
            where.append(" AND o.kind = ?");
            values.add(filter.kind().name());
        }
        if (filter.runningTo() != null) {
            where.append(" AND o.starts_on <= ?");
            values.add(filter.runningTo());
        }
        if (filter.runningFrom() != null) {
            where.append(" AND (o.ends_on IS NULL OR o.ends_on >= ?)");
            values.add(filter.runningFrom());
        }
        String sql = "SELECT " + OFFER_COLUMNS + ", un.unit_name,"
                + " (SELECT COUNT(*) FROM offer_target t WHERE t.offer_id = o.id) AS targets,"
                + " (SELECT COUNT(*) FROM sales s WHERE s.offer_id = o.id) AS used_lines,"
                + " (SELECT COALESCE(SUM(s.offer_discount), 0) FROM sales s WHERE s.offer_id = o.id) AS given"
                + " FROM offer o LEFT JOIN units un ON un.unit_id = o.unit_id"
                + where
                + " ORDER BY FIELD(o.status, 'ACTIVE', 'DRAFT', 'STOPPED'), o.starts_on DESC, o.id DESC";
        return new Statement(sql, values);
    }

    /** {@code !}, {@code %} and {@code _} taken literally under {@code ESCAPE '!'}. */
    static String escape(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static String placeholders(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("at least one id");
        }
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private OfferQuery() {
    }
}
