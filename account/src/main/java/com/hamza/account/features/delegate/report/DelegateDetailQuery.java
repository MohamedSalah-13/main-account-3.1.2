package com.hamza.account.features.delegate.report;

/**
 * One delegate's period, taken apart: by customer, by area, by item, by group - and his
 * collections one by one. Pinned character for character by {@code DelegateDetailQueryTest},
 * with each statement's parameter count.
 *
 * <p><b>It defines nothing of its own</b>, and every breakdown adds up to the one figure
 * {@code DelegateActivityQuery.ACTIVITY_SQL} already reports for the same delegate and period -
 * which is the rule that makes a detail report worth opening: it explains a number, it does not
 * offer a second one.
 *
 * <ul>
 *   <li><b>By customer and by area are read off the documents</b>: {@code total - discount}, the
 *       net the activity query, {@code OpenInvoiceQuery} and the account views read. Their rows
 *       add up to the delegate's sales and returns exactly.</li>
 *   <li><b>By item and by group are read off the lines</b>: {@code total_sel_price - discount},
 *       which is what a document's {@code total} is the sum of. A discount taken on the whole
 *       invoice belongs to no item, and sharing it out among them would be an invented rule - so
 *       it is not shared out. {@link #HEADER_DISCOUNT_SQL} reports it as a figure of its own, and
 *       lines less header discounts is the same net as above.</li>
 * </ul>
 *
 * <p>A return counts in the period of the <b>return</b>, as it does for the commission. Each
 * side is grouped on its own before the two are put together: joined first and grouped after,
 * a customer's every invoice would be multiplied by his every return.
 *
 * <p>The third column differs by family and is named for what it is: a count of
 * {@code documents} off the headers, a {@code quantity} in base units off the lines
 * ({@code quantity * type_value}, as {@code quantity_items_table} counts it; returns negative).
 */
public final class DelegateDetailQuery {

    /** Bound to every breakdown and to the header discounts: delegate, from, to - twice. */
    public static final int BREAKDOWN_PARAMETERS = 6;

    public static final int COLLECTIONS_PARAMETERS = 3;

    public static final String BY_CUSTOMER_SQL = documents(
            "ts.sup_code", "tr.sup_id", "", "",
            "JOIN custom n ON n.id = m.key_id", "n.name");

    /** The area is the customer's today: an invoice does not carry one. */
    public static final String BY_AREA_SQL = documents(
            "cu.area_id", "cu.area_id",
            "JOIN custom cu ON cu.id = ts.sup_code", "JOIN custom cu ON cu.id = tr.sup_id",
            "LEFT JOIN table_area n ON n.id = m.key_id", "COALESCE(n.area_name, '')");

    public static final String BY_ITEM_SQL = lines(
            "s.num", "sr.item_id", "", "",
            "JOIN items n ON n.id = m.key_id", "n.nameItem");

    public static final String BY_GROUP_SQL = lines(
            "i.sub_num", "i.sub_num",
            "JOIN items i ON i.id = s.num", "JOIN items i ON i.id = sr.item_id",
            "JOIN sub_group n ON n.id = m.key_id", "n.name");

    /**
     * What was taken off whole invoices, and off whole returns: the difference between the lines
     * and the documents' net. Two figures in one row - sales first.
     */
    public static final String HEADER_DISCOUNT_SQL = """
            SELECT (SELECT COALESCE(SUM(discount), 0)
                    FROM total_sales
                    WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?),
                   (SELECT COALESCE(SUM(discount), 0)
                    FROM total_sales_re
                    WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?)""";

    /**
     * His collections, one by one: what, from whom, and against which invoice. It reads
     * {@code customers_accounts.delegate_id} - written at entry, never derived (V71) - and only
     * rows that moved cash, so a debit or a credit note is nobody's collection here either.
     * {@code numberInv = 0} is a payment left on account.
     */
    public static final String COLLECTIONS_SQL = """
            SELECT ca.account_num,
                   ca.account_date,
                   cu.name,
                   ca.numberInv,
                   ca.paid,
                   COALESCE(t.t_name, '') AS treasury_name
            FROM customers_accounts ca
                     JOIN custom cu ON cu.id = ca.account_code
                     LEFT JOIN treasury t ON t.id = ca.treasury_id
            WHERE ca.delegate_id = ?
              AND ca.account_date BETWEEN ? AND ?
              AND ca.paid <> 0
            ORDER BY ca.account_date, ca.account_num""";

    private DelegateDetailQuery() {
    }

    /** A breakdown read off the documents themselves. */
    private static String documents(String salesKey, String returnsKey, String salesJoin, String returnsJoin,
                                    String nameJoin, String name) {
        return tidy("""
                SELECT m.key_id,
                       %6$s AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT %1$s AS key_id, COUNT(*) AS measure, SUM(ts.total - ts.discount) AS sales, 0 AS returns
                      FROM total_sales ts %3$s
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY %1$s
                      UNION ALL
                      SELECT %2$s, 0, 0, SUM(tr.total - tr.discount)
                      FROM total_sales_re tr %4$s
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY %2$s) m
                         %5$s
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""".formatted(
                salesKey, returnsKey, salesJoin, returnsJoin, nameJoin, name));
    }

    /** A breakdown read off the lines. */
    private static String lines(String salesKey, String returnsKey, String salesJoin, String returnsJoin,
                                String nameJoin, String name) {
        return tidy("""
                SELECT m.key_id,
                       %6$s AS key_name,
                       SUM(m.measure) AS measure,
                       SUM(m.sales) AS sales,
                       SUM(m.returns) AS sales_returns
                FROM (SELECT %1$s AS key_id, SUM(s.quantity * s.type_value) AS measure,
                             SUM(s.total_sel_price - s.discount) AS sales, 0 AS returns
                      FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number %3$s
                      WHERE ts.delegate_id = ? AND ts.invoice_date BETWEEN ? AND ?
                      GROUP BY %1$s
                      UNION ALL
                      SELECT %2$s, -SUM(sr.quantity * sr.type_value), 0, SUM(sr.total_sel_price - sr.discount)
                      FROM sales_re sr JOIN total_sales_re tr ON tr.id = sr.invoice_number %4$s
                      WHERE tr.delegate_id = ? AND tr.invoice_date BETWEEN ? AND ?
                      GROUP BY %2$s) m
                         %5$s
                GROUP BY m.key_id, key_name
                ORDER BY SUM(m.sales) - SUM(m.returns) DESC, key_name""".formatted(
                salesKey, returnsKey, salesJoin, returnsJoin, nameJoin, name));
    }

    /** An empty join leaves a trailing blank before the line break; the pinned text has none. */
    private static String tidy(String sql) {
        return sql.replace(" \n", "\n");
    }
}
