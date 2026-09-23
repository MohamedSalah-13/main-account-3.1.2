package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.stockcount.StockCountHistoryQuery;

/**
 * Every statement the profit and loss screen reads beside {@code ProfitLossDao}, pinned by
 * {@code ProfitLossStatementQueryTest}.
 *
 * <p><b>Each bound sits inside each branch, on the date column</b>, and a document's cost is summed
 * from its own lines by its number. Not {@code document_profit} itself, and on purpose: that view joins
 * every sale to a {@code GROUP BY} over the whole {@code sales} line table, which MySQL builds whatever the
 * outer query filters on - the trap {@code ExpenseReportQuery} records. The arithmetic is the view's own
 * ({@code total - discount}, and the lines' {@code SUM(total_buy_price)}), which the test finds in
 * {@code R__views.sql}, and {@code ProfitLossStatementDatabaseAcceptanceTest} holds the figures to the
 * statement's on MySQL.</p>
 */
public final class ProfitLossStatementQuery {

    /** The cost of one sales invoice, from its lines - {@code document_profit}'s cost of sales. */
    static final String SALE_COST = "(SELECT COALESCE(SUM(s.total_buy_price), 0) FROM sales s"
            + " WHERE s.invoice_number = ts.invoice_number)";
    /** The cost of the goods on one sales return. A return line points at its return by the same column. */
    static final String RETURN_COST = "(SELECT COALESCE(SUM(r.total_buy_price), 0) FROM sales_re r"
            + " WHERE r.invoice_number = tsr.id)";

    /** Parameters: from, to, from, to. */
    public static final String BREAKDOWN_SQL = """
            SELECT COALESCE(SUM(gross_sales), 0) AS gross_sales,
                   COALESCE(SUM(invoice_discount), 0) AS invoice_discounts,
                   COALESCE(SUM(returned), 0) AS returns_net,
                   COALESCE(SUM(cost_sold), 0) AS cost_of_sold,
                   COALESCE(SUM(cost_returned), 0) AS cost_of_returned
            FROM (SELECT ts.total AS gross_sales, ts.discount AS invoice_discount, 0 AS returned,
                         %1$s AS cost_sold, 0 AS cost_returned
                  FROM total_sales ts
                  WHERE ts.invoice_date BETWEEN ? AND ?
                  UNION ALL
                  SELECT 0, 0, tsr.total - tsr.discount, 0, %2$s
                  FROM total_sales_re tsr
                  WHERE tsr.invoice_date BETWEEN ? AND ?) documents""".formatted(SALE_COST, RETURN_COST);

    /**
     * Each main heading's total, its sub-headings rolled into it. Parameters: from, to. The heading is a
     * {@code LEFT JOIN} so no expense is lost from the total whatever becomes of its heading's row.
     */
    public static final String EXPENSES_BY_HEADING_SQL = """
            SELECT COALESCE(m.id, e.id, d.type_code) AS heading_id,
                   MAX(COALESCE(m.expenses_name, e.expenses_name)) AS heading_name,
                   SUM(d.amount) AS total
            FROM expenses_details d
                     LEFT JOIN expenses e ON e.id = d.type_code
                     LEFT JOIN expenses m ON m.id = e.parent_id
            WHERE d.date BETWEEN ? AND ?
            GROUP BY COALESCE(m.id, e.id, d.type_code)""";

    /**
     * What the posted counts dated in the period found, valued at each item's buy price today. The
     * difference of a line is {@link StockCountHistoryQuery#DIFFERENCE}, the adjustment's own expression
     * in {@code R__views.sql}, in base units. Parameters: from, to.
     */
    public static final String STOCK_COUNT_SQL = """
            SELECT COALESCE(SUM(CASE WHEN difference < 0 THEN -difference * buy_price ELSE 0 END), 0) AS shortage,
                   COALESCE(SUM(CASE WHEN difference > 0 THEN difference * buy_price ELSE 0 END), 0) AS surplus
            FROM (SELECT %s AS difference, i.buy_price AS buy_price
                  FROM stock_count_lines scl
                           JOIN stock_count sc ON sc.id = scl.count_id
                           JOIN items i ON i.id = scl.item_id
                  WHERE sc.status = 'POSTED'
                    AND sc.count_date BETWEEN ? AND ?) counted""".formatted(StockCountHistoryQuery.DIFFERENCE);

    /**
     * What each shift closed in the period recorded as counted less expected. Parameters: the first day,
     * and the day after the last - {@code close_time} is a moment, and a half-open range keeps the index.
     */
    public static final String TILL_SQL = """
            SELECT COALESCE(SUM(CASE WHEN difference_amount < 0 THEN -difference_amount ELSE 0 END), 0) AS shortage,
                   COALESCE(SUM(CASE WHEN difference_amount > 0 THEN difference_amount ELSE 0 END), 0) AS surplus
            FROM shift_close_snapshots
            WHERE close_time >= ? AND close_time < ?""";

    /**
     * What a row of the statement is made of: its sales invoices, its sales returns and its expenses, a
     * return signed against the sales as {@code document_profit} signs it. Parameters: from, to, three
     * times.
     */
    public static final String MOVEMENTS_SQL = """
            SELECT kind, number, movement_date, name, note, net_sales, cost, expense
            FROM (SELECT 'SALE' AS kind, ts.invoice_number AS number, ts.invoice_date AS movement_date,
                         COALESCE(c.name, '') AS name, COALESCE(ts.notes, '') AS note,
                         ts.total - ts.discount AS net_sales, %1$s AS cost, 0 AS expense
                  FROM total_sales ts
                           LEFT JOIN custom c ON c.id = ts.sup_code
                  WHERE ts.invoice_date BETWEEN ? AND ?
                  UNION ALL
                  SELECT 'SALE_RETURN', tsr.id, tsr.invoice_date, COALESCE(c.name, ''), COALESCE(tsr.notes, ''),
                         -(tsr.total - tsr.discount), -%2$s, 0
                  FROM total_sales_re tsr
                           LEFT JOIN custom c ON c.id = tsr.sup_id
                  WHERE tsr.invoice_date BETWEEN ? AND ?
                  UNION ALL
                  SELECT 'EXPENSE', d.id, d.date, COALESCE(e.expenses_name, ''), COALESCE(d.notes, ''), 0, 0, d.amount
                  FROM expenses_details d
                           LEFT JOIN expenses e ON e.id = d.type_code
                  WHERE d.date BETWEEN ? AND ?) movements
            ORDER BY movement_date, kind, number""".formatted(SALE_COST, RETURN_COST);

    private ProfitLossStatementQuery() {
    }
}
