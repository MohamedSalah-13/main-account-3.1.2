package com.hamza.account.features.delegate.trend;

/**
 * One delegate's days: what he sold, what came back, and what he collected.
 * <p>
 * <b>It defines nothing of its own.</b> Each of the four sums is written here once and is the text
 * {@code DelegateActivityQuery.ACTIVITY_SQL} uses - {@code DelegateTrendQueryTest} finds each in it -
 * so a month on this chart and the same month's row of the performance report are one figure: net
 * sales are {@code total - discount} of his invoices less his returns, a return counting on the day
 * of the return; collected is cash in both directions - the cash part of his invoices, less what was
 * refunded in cash on his customers' returns, plus the collections written to him at entry
 * ({@code customers_accounts.delegate_id}, V71). A credit note is {@code purchase} and is not read.
 * <p>
 * Each source is grouped by day on its own before the three are added, for the reason the activity
 * query gives: joining first multiplies an invoice by every collection of its day. Nine placeholders:
 * the delegate, then the two dates, for each of the three sources.
 */
public final class DelegateTrendQuery {

    /** A document's net - the performance report's sales and returns. */
    static final String NET = "SUM(total - discount)";
    /** The cash part of a sale. */
    static final String CASH_ON_SALES = "SUM(paid_up)";
    /** The cash handed back on a return. */
    static final String CASH_REFUNDED = "SUM(paid_from_treasury)";
    /** A collection written to the delegate. */
    static final String COLLECTED = "SUM(paid)";

    public static final String DAILY_SQL = """
            SELECT movements.day                         AS day,
                   ROUND(SUM(movements.sales), 2)         AS sales,
                   ROUND(SUM(movements.sales_returns), 2) AS sales_returns,
                   ROUND(SUM(movements.collected), 2)     AS collected
            FROM (SELECT invoice_date AS day, %1$s AS sales, 0 AS sales_returns, %2$s AS collected
                  FROM total_sales
                  WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?
                  GROUP BY invoice_date
                  UNION ALL
                  SELECT invoice_date, 0, %1$s, -%3$s
                  FROM total_sales_re
                  WHERE delegate_id = ? AND invoice_date BETWEEN ? AND ?
                  GROUP BY invoice_date
                  UNION ALL
                  SELECT account_date, 0, 0, %4$s
                  FROM customers_accounts
                  WHERE delegate_id = ? AND account_date BETWEEN ? AND ?
                  GROUP BY account_date) movements
            GROUP BY movements.day
            ORDER BY movements.day"""
            .formatted(NET, CASH_ON_SALES, CASH_REFUNDED, COLLECTED);

    private DelegateTrendQuery() {
    }
}
