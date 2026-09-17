package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseQuery;

/**
 * Every statement the expense reports read, pinned by {@code ExpenseReportQueryTest}.
 * <p>
 * <b>Each is built from the list's own joins and the list's own {@code WHERE}</b>
 * ({@link ExpenseQuery#fromSql()}, {@link ExpenseQuery#whereSql}), and bound with
 * {@link ExpenseQuery#whereValues}. So a report over a filter and the list over the same filter are the
 * same set of expenses by construction: a report that wrote its conditions again could agree with the
 * list on every test case and still part company on the first filter somebody adds to one of them.
 * <p>
 * <b>The statements answer amounts per heading and per day, never per period.</b> Which days make a
 * week or a month is {@code TrendGranularity}'s, in Java, with a test - the rule the collections trend
 * set, so the statement, the chart and these reports cannot hold two definitions of a week.
 */
public final class ExpenseReportQuery {

    /**
     * What a sale earned net of its discounts, as {@code document_profit} states it - and a return, signed
     * against it. {@code ExpenseReportQueryTest} finds both in {@code R__views.sql}: this is that view's
     * {@code net_revenue}, read straight off the two header tables.
     */
    public static final String SALE_NET_REVENUE = "ts.total - ts.discount";
    public static final String RETURN_NET_REVENUE = "-(tsr.total - tsr.discount)";

    /**
     * Net sales per day between two days, both included. Parameters: from, to, from, to.
     * <p>
     * <b>Not {@code document_profit} itself, and on purpose.</b> That view joins every sale to a
     * {@code GROUP BY} over the whole {@code sales} line table to reach the cost, and MySQL builds that
     * aggregate whatever the outer query filters on - the trap that made the totals screen slow. The ratio
     * needs the revenue alone, which is the header's own arithmetic; the bounds sit inside each branch, on
     * the date column.
     */
    public static final String NET_SALES_DAYS_SQL = """
            SELECT sale_date, SUM(net_revenue) AS net_sales
            FROM (SELECT ts.invoice_date AS sale_date, %s AS net_revenue
                  FROM total_sales ts
                  WHERE ts.invoice_date BETWEEN ? AND ?
                  UNION ALL
                  SELECT tsr.invoice_date, %s
                  FROM total_sales_re tsr
                  WHERE tsr.invoice_date BETWEEN ? AND ?) sales
            GROUP BY sale_date""".formatted(SALE_NET_REVENUE, RETURN_NET_REVENUE);

    private ExpenseReportQuery() {
    }

    /** The count and total per heading, each sub-heading under itself. The tree is assembled in Java. */
    public static String headingTotalsSql(ExpenseFilter filter) {
        return "SELECT d.type_code AS heading_id, COUNT(*) AS expense_count, SUM(d.amount) AS total\n"
                + ExpenseQuery.fromSql() + ExpenseQuery.whereSql(filter)
                + "GROUP BY d.type_code";
    }

    /** The total per heading per day - what the year's matrix files into months. */
    public static String headingDaysSql(ExpenseFilter filter) {
        return "SELECT d.type_code AS heading_id, d.date AS expense_date, SUM(d.amount) AS total\n"
                + ExpenseQuery.fromSql() + ExpenseQuery.whereSql(filter)
                + "GROUP BY d.type_code, d.date";
    }

    /** The total per day, over every heading the filter lets through. */
    public static String daysSql(ExpenseFilter filter) {
        return "SELECT d.date AS expense_date, SUM(d.amount) AS total\n"
                + ExpenseQuery.fromSql() + ExpenseQuery.whereSql(filter)
                + "GROUP BY d.date";
    }

    /**
     * The count and total per value of one dimension. The label is an aggregate of the name beside the
     * key, so {@code only_full_group_by} has nothing to object to and a till renamed mid-period is still
     * one row, under its key.
     */
    public static String dimensionSql(ExpenseFilter filter, ExpenseDimension dimension) {
        return "SELECT " + dimension.keySql() + " AS dimension_key, " + dimension.labelSql()
                + " AS dimension_label, COUNT(*) AS expense_count, SUM(d.amount) AS total\n"
                + ExpenseQuery.fromSql() + ExpenseQuery.whereSql(filter)
                + "GROUP BY " + dimension.keySql();
    }
}
