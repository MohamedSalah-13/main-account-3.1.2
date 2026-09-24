package com.hamza.account.features.pricing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * The two reports of phase A (docs/pricing-and-offers-plan.md ق-س٣ and ق-س٤), each a page and its
 * count - or its totals - built from <b>one</b> {@code WHERE} and one binder, so the pager and the figures
 * above it cannot describe a different set from the rows under them. Only identifiers this class owns
 * enter the SQL; every value is bound.
 */
public final class TierReportQuery {

    /** A statement and the values it binds, in order. */
    public record Statement(String sql, List<Object> parameters) {
    }

    private TierReportQuery() {
    }

    // ---- items with no price on a tier in use ------------------------------------------------

    private static final String MISSING_FROM = " FROM items i";

    /**
     * An item missing a price on any tier in use - tier 1 included, which the item screen requires but
     * a row written another way may lack - narrowed by a name or an exact code.
     */
    static String missingWhere(List<Integer> activeTiers, String text, List<Object> parameters) {
        StringJoiner missing = new StringJoiner(" OR ", "(", ")");
        for (int tier : activeTiers) {
            missing.add("i." + PriceTiers.itemColumn(tier) + " <= 0");
        }
        StringBuilder where = new StringBuilder(" WHERE ").append(activeTiers.isEmpty() ? "1 = 0" : missing.toString());
        if (text != null && !text.isBlank()) {
            where.append(" AND (i.nameItem LIKE ? OR i.barcode = ?)");
            parameters.add("%" + text.trim() + "%");
            parameters.add(text.trim());
        }
        return where.toString();
    }

    public static Statement missingPage(List<Integer> activeTiers, String text, int limit, int offset) {
        List<Object> parameters = new ArrayList<>();
        String sql = "SELECT i.id, i.barcode, i.nameItem, i.sel_price1, i.sel_price2, i.sel_price3"
                + MISSING_FROM + missingWhere(activeTiers, text, parameters)
                + " ORDER BY i.nameItem, i.id LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);
        return new Statement(sql, parameters);
    }

    public static Statement missingCount(List<Integer> activeTiers, String text) {
        List<Object> parameters = new ArrayList<>();
        return new Statement("SELECT COUNT(*)" + MISSING_FROM + missingWhere(activeTiers, text, parameters),
                parameters);
    }

    // ---- sales below their list price ---------------------------------------------------------

    /**
     * Joined with a space on each side, never as a text block: a text block strips its common indent, so
     * one opening with " FROM" loses the space it is concatenated by - the first MySQL run read
     * {@code ts.price_tier_idFROM}.
     */
    private static final String BELOW_FROM = " FROM sales s"
            + " JOIN total_sales ts ON ts.invoice_number = s.invoice_number"
            + " JOIN items i ON i.id = s.num"
            + " JOIN units un ON un.unit_id = s.type"
            + " LEFT JOIN custom c ON c.id = ts.sup_code"
            + " LEFT JOIN users u ON u.id = ts.user_id";

    /**
     * What the line gave away below its list: the difference times the quantity - what a price typed
     * under the list cost the shop, which no report used to see.
     */
    static final String GIVEN = "ROUND((s.list_price - s.price) * s.quantity, 2)";

    /**
     * A line priced below its list by more than half a piastre, in a period, narrowed by a customer's,
     * an item's or a user's name. Lines saved before V84 have no list price and are never here.
     */
    static String belowListWhere(LocalDate from, LocalDate to, String text, List<Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE s.list_price IS NOT NULL"
                + " AND s.price < s.list_price - 0.005"
                + " AND ts.invoice_date BETWEEN ? AND ?");
        parameters.add(from);
        parameters.add(to);
        if (text != null && !text.isBlank()) {
            where.append(" AND (c.name LIKE ? OR i.nameItem LIKE ? OR u.user_name LIKE ?)");
            String like = "%" + text.trim() + "%";
            parameters.add(like);
            parameters.add(like);
            parameters.add(like);
        }
        return where.toString();
    }

    public static Statement belowListPage(LocalDate from, LocalDate to, String text, int limit, int offset) {
        List<Object> parameters = new ArrayList<>();
        String sql = "SELECT ts.invoice_number, ts.invoice_date, c.name AS customer, u.user_name,"
                + " i.nameItem, un.unit_name, s.quantity, s.list_price, s.price, " + GIVEN + " AS given,"
                + " ts.price_tier_id"
                + BELOW_FROM + belowListWhere(from, to, text, parameters)
                + " ORDER BY ts.invoice_date DESC, ts.invoice_number DESC, s.id LIMIT ? OFFSET ?";
        parameters.add(limit);
        parameters.add(offset);
        return new Statement(sql, parameters);
    }

    /** How many lines, on how many invoices, and what they gave away - the whole filtered set. */
    public static Statement belowListSummary(LocalDate from, LocalDate to, String text) {
        List<Object> parameters = new ArrayList<>();
        return new Statement("SELECT COUNT(*), COUNT(DISTINCT s.invoice_number), COALESCE(SUM(" + GIVEN + "), 0)"
                + BELOW_FROM + belowListWhere(from, to, text, parameters), parameters);
    }
}
