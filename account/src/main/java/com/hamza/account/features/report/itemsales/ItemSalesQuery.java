package com.hamza.account.features.report.itemsales;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import com.hamza.account.features.items.ItemCatalogSql;

/**
 * The statements behind the item sales report, built from {@link DocumentTableSpec} so the sale's and the
 * return's different spellings ({@code num}/{@code item_id}, {@code invoice_number}/{@code id}) are written
 * in one place, and from {@link ItemNetLines#lineAmount} so a line is worth what every other item report
 * says it is worth.
 *
 * <p><b>Each side is grouped before the union</b> - the rule that keeps an invoice from being multiplied
 * by its returns - and <b>the cost is selected only when it is asked for</b>: a reader who may not see a
 * profit is not sent one, which is the rule the employees' salaries set.</p>
 */
public final class ItemSalesQuery {

    /** The period for the sales and again for the returns, bound before the filter's own parameters. */
    public static final int ROWS_PERIOD_PARAMETERS = 4;
    /** The period and the item, for the sales and again for the returns. */
    public static final int LINES_PARAMETERS = 6;

    private static final DocumentTableSpec SALES = DocumentTableSpec.SALES;
    private static final DocumentTableSpec RETURNS = DocumentTableSpec.SALES_RETURN;

    private ItemSalesQuery() {
    }

    /**
     * One row per item named on a sale or a sales return dated in the period, narrowed by the items
     * screen's {@code WHERE}.
     *
     * @param withCost whether the lines' recorded cost is selected - only for a reader who may see a profit
     */
    public static String rowsSql(ItemCatalogSql.Statement filter, boolean withCost) {
        String cost = withCost ? ",\n       s.cost AS cost" : "";
        String sumCost = withCost ? ",\n             SUM(m.cost) AS cost" : "";
        return """
                SELECT items.id AS item_id,
                       items.nameItem AS name_item,
                       sg.name AS sub_group_name,
                       mg.name_g AS main_group_name,
                       u.unit_name AS unit_name,
                       s.sold_quantity AS sold_quantity,
                       s.returned_quantity AS returned_quantity,
                       s.invoices AS invoices,
                       s.sold AS sold,
                       s.returned AS returned%s
                FROM (SELECT m.item_id,
                             SUM(m.sold_quantity) AS sold_quantity,
                             SUM(m.returned_quantity) AS returned_quantity,
                             SUM(m.invoices) AS invoices,
                             SUM(m.sold) AS sold,
                             SUM(m.returned) AS returned%s
                      FROM (%s
                            UNION ALL
                            %s) m
                      GROUP BY m.item_id) s
                         JOIN items ON items.id = s.item_id
                         LEFT JOIN sub_group sg ON sg.id = items.sub_num
                         LEFT JOIN main_group mg ON mg.id = sg.main_id
                         LEFT JOIN units u ON u.unit_id = items.unit_id
                """.formatted(cost, sumCost, side(SALES, "d", "h", false, withCost),
                side(RETURNS, "r", "rh", true, withCost))
                + filter.where();
    }

    /**
     * One item's lines over the period, gathered by the unit written and the price carried - the sales
     * first, then the returns, the larger unit first within each.
     */
    public static String linesSql() {
        return lines(SALES, "d", "h", false) + "\nUNION ALL\n" + lines(RETURNS, "r", "rh", true)
                + "\nORDER BY is_return, factor DESC, price DESC";
    }

    /** One side grouped per item: a sale fills the sold columns, a return the returned ones. */
    private static String side(DocumentTableSpec spec, String line, String header, boolean isReturn,
                               boolean withCost) {
        String quantity = "SUM(" + line + ".quantity * " + line + ".type_value)";
        String amount = "SUM(" + ItemNetLines.lineAmount(spec, line) + ")";
        String cost = "SUM(" + line + ".total_buy_price)";
        return "SELECT " + line + "." + spec.lineItem() + " AS item_id, "
                + (isReturn ? "0" : quantity) + " AS sold_quantity, "
                + (isReturn ? quantity : "0") + " AS returned_quantity, "
                + (isReturn ? "0" : "COUNT(DISTINCT " + line + "." + DocumentTableSpec.LINE_DOCUMENT + ")")
                + " AS invoices, "
                + (isReturn ? "0" : amount) + " AS sold, "
                + (isReturn ? amount : "0") + " AS returned"
                + (withCost ? ", " + (isReturn ? "-" + cost : cost) + " AS cost" : "")
                + " FROM " + spec.lineTable() + " " + line
                + " JOIN " + spec.table() + " " + header
                + " ON " + header + "." + spec.key() + " = " + line + "." + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE " + header + "." + spec.dateColumn() + " BETWEEN ? AND ?"
                + " GROUP BY " + line + "." + spec.lineItem();
    }

    private static String lines(DocumentTableSpec spec, String line, String header, boolean isReturn) {
        return "SELECT " + (isReturn ? 1 : 0) + " AS is_return, u.unit_name AS unit_name, "
                + line + ".type_value AS factor, " + line + ".price AS price, "
                + "SUM(" + line + ".quantity) AS quantity, SUM(" + line + ".discount) AS discount, "
                + "SUM(" + ItemNetLines.lineAmount(spec, line) + ") AS amount, "
                + "COUNT(DISTINCT " + line + "." + DocumentTableSpec.LINE_DOCUMENT + ") AS documents"
                + " FROM " + spec.lineTable() + " " + line
                + " JOIN " + spec.table() + " " + header
                + " ON " + header + "." + spec.key() + " = " + line + "." + DocumentTableSpec.LINE_DOCUMENT
                + " LEFT JOIN units u ON u.unit_id = " + line + ".type"
                + " WHERE " + header + "." + spec.dateColumn() + " BETWEEN ? AND ?"
                + " AND " + line + "." + spec.lineItem() + " = ?"
                + " GROUP BY " + line + ".type, u.unit_name, " + line + ".type_value, " + line + ".price";
    }
}
