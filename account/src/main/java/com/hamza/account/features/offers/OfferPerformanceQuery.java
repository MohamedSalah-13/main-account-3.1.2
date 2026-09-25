package com.hamza.account.features.offers;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;

/**
 * The statements behind the offers' performance report (docs/pricing-and-offers-plan.md phase E), pinned by
 * {@code OfferPerformanceQueryTest}. Built from {@link DocumentTableSpec} so the sale's and the return's
 * spellings are written once, and from {@link ItemNetLines#lineAmount} so a line is worth what every other
 * report says it is worth - after its own discount, of which the offer's is a part.
 *
 * <p><b>Each side is grouped before the union</b>, which keeps an invoice from being multiplied by its
 * returns; a return counts in the period it is dated in, whatever the date of the invoice it reverses, and
 * gives back its share of the offer's discount and of the units it covered. <b>The cost is selected only when
 * it is asked for</b>: a reader who may not see a profit is not sent one.</p>
 */
public final class OfferPerformanceQuery {

    /** The period for the sales and again for the returns. */
    public static final int FIGURES_PARAMETERS = 4;
    /** The period and the offer, for the sales and again for the returns. */
    public static final int ITEMS_PARAMETERS = 6;

    private static final DocumentTableSpec SALES = DocumentTableSpec.SALES;
    private static final DocumentTableSpec RETURNS = DocumentTableSpec.SALES_RETURN;

    private OfferPerformanceQuery() {
    }

    /** One row per offer a sale line or a return line dated in the period names. */
    public static String figuresSql(boolean withCost) {
        return "SELECT m.offer_id, SUM(m.invoices) AS invoices, SUM(m.lines_count) AS lines_count,"
                + " SUM(m.sold_quantity) AS sold_quantity, SUM(m.returned_quantity) AS returned_quantity,"
                + " SUM(m.covered) AS covered, SUM(m.given) AS given, SUM(m.given_back) AS given_back,"
                + " SUM(m.sold) AS sold, SUM(m.returned) AS returned"
                + (withCost ? ", SUM(m.cost) AS cost" : "")
                + " FROM (" + side(SALES, "d", "h", false, withCost, false)
                + " UNION ALL " + side(RETURNS, "r", "rh", true, withCost, false) + ") m"
                + " GROUP BY m.offer_id";
    }

    /**
     * How many sales dated in the period any offer reached - counted once each: an invoice holding a bundle and
     * a price offer is one invoice, where the rows' own counts would call it two.
     */
    public static String invoicesSql() {
        return "SELECT COUNT(DISTINCT d." + DocumentTableSpec.LINE_DOCUMENT + ") FROM " + SALES.lineTable() + " d"
                + " JOIN " + SALES.table() + " h ON h." + SALES.key() + " = d." + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE h." + SALES.dateColumn() + " BETWEEN ? AND ? AND d.offer_id IS NOT NULL";
    }

    /** One offer's items over the period - the row's drawer - the most it sold for first. */
    public static String itemsSql(boolean withCost) {
        return "SELECT m.item_id, items.nameItem AS name_item, u.unit_name AS unit_name,"
                + " SUM(m.sold_quantity) AS sold_quantity, SUM(m.returned_quantity) AS returned_quantity,"
                + " SUM(m.given) - SUM(m.given_back) AS discount, SUM(m.sold) - SUM(m.returned) AS net"
                + (withCost ? ", SUM(m.cost) AS cost" : "")
                + " FROM (" + side(SALES, "d", "h", false, withCost, true)
                + " UNION ALL " + side(RETURNS, "r", "rh", true, withCost, true) + ") m"
                + " JOIN items ON items.id = m.item_id LEFT JOIN units u ON u.unit_id = items.unit_id"
                + " GROUP BY m.item_id, items.nameItem, u.unit_name ORDER BY net DESC, m.item_id";
    }

    /**
     * One side grouped per offer - or, for one offer, per item: a sale fills the sold columns, a return the
     * returned ones, and takes back the units it covered.
     */
    private static String side(DocumentTableSpec spec, String line, String header, boolean isReturn,
                               boolean withCost, boolean byItem) {
        String key = byItem ? line + "." + spec.lineItem() : line + ".offer_id";
        String quantity = "SUM(" + line + ".quantity * " + line + ".type_value)";
        String amount = "SUM(" + ItemNetLines.lineAmount(spec, line) + ")";
        String covered = "SUM(" + line + ".offer_quantity)";
        String given = "SUM(" + line + ".offer_discount)";
        String cost = "SUM(" + line + ".total_buy_price)";
        return "SELECT " + key + (byItem ? " AS item_id" : " AS offer_id") + ", "
                + (isReturn ? "0" : "COUNT(DISTINCT " + line + "." + DocumentTableSpec.LINE_DOCUMENT + ")")
                + " AS invoices, "
                + (isReturn ? "0" : "COUNT(*)") + " AS lines_count, "
                + (isReturn ? "0" : quantity) + " AS sold_quantity, "
                + (isReturn ? quantity : "0") + " AS returned_quantity, "
                + (isReturn ? "-" + covered : covered) + " AS covered, "
                + (isReturn ? "0" : given) + " AS given, "
                + (isReturn ? given : "0") + " AS given_back, "
                + (isReturn ? "0" : amount) + " AS sold, "
                + (isReturn ? amount : "0") + " AS returned"
                + (withCost ? ", " + (isReturn ? "-" + cost : cost) + " AS cost" : "")
                + " FROM " + spec.lineTable() + " " + line
                + " JOIN " + spec.table() + " " + header
                + " ON " + header + "." + spec.key() + " = " + line + "." + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE " + header + "." + spec.dateColumn() + " BETWEEN ? AND ?"
                + (byItem ? " AND " + line + ".offer_id = ?" : " AND " + line + ".offer_id IS NOT NULL")
                + " GROUP BY " + key;
    }
}
