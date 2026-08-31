package com.hamza.account.model.dao;

import com.hamza.account.model.domain.DashboardPeriodSummary;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Backs the dashboard's period selector. Mirrors the arithmetic
 * {@code daily_dashboard_report} (see R__views.sql) uses for "today", but over an
 * arbitrary inclusive [from, to] range instead of {@code CURDATE()} - there is no
 * per-period view to keep in step, so this is one parametrized query instead.
 */
public class DashboardPeriodDao extends AbstractDao<DashboardPeriodSummary> {

    private static final String SQL = """
            SELECT
                (SELECT COUNT(invoice_number) FROM total_sales WHERE invoice_date BETWEEN ? AND ?) AS sales_count,
                COALESCE((SELECT SUM(total) FROM total_sales WHERE invoice_date BETWEEN ? AND ?), 0) AS sales_total,
                (SELECT COUNT(invoice_number) FROM total_buy WHERE invoice_date BETWEEN ? AND ?) AS purchases_count,
                COALESCE((SELECT SUM(total) FROM total_buy WHERE invoice_date BETWEEN ? AND ?), 0) AS purchases_total,
                (
                    COALESCE((SELECT SUM(paid_up) FROM total_sales WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(paid_to_treasury) FROM total_buy_re WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(paid) FROM customers_accounts WHERE account_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter BETWEEN ? AND ? AND deposit_or_expenses = 1), 0)
                ) AS total_receipts,
                (
                    COALESCE((SELECT SUM(paid_up) FROM total_buy WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(paid_from_treasury) FROM total_sales_re WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(paid) FROM suppliers_accounts WHERE account_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter BETWEEN ? AND ? AND deposit_or_expenses = 2), 0) +
                    COALESCE((SELECT SUM(amount) FROM expenses_details WHERE date BETWEEN ? AND ?), 0)
                ) AS total_payments_and_expenses,
                (
                    COALESCE((SELECT SUM(discount) FROM total_sales WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(discount) FROM total_buy WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(discount) FROM total_sales_re WHERE invoice_date BETWEEN ? AND ?), 0) +
                    COALESCE((SELECT SUM(discount) FROM total_buy_re WHERE invoice_date BETWEEN ? AND ?), 0)
                ) AS total_discounts
            """;

    public DashboardPeriodDao() {
        super();
    }

    // Every placeholder pair in SQL is the same (from, to) BETWEEN bound - 17 pairs, 34
    // params total. It was 15 until customers_accounts and suppliers_accounts were added
    // to the two cash columns: this view and daily_dashboard_report counted a day's money
    // without the collections and the payments made against party accounts, so they
    // disagreed with treasury_balance by exactly those. Counting the pairs by hand is the
    // price of one constant; DashboardPeriodSummaryTest counts them from the SQL itself so
    // a subquery added without touching this line fails the build rather than the screen.
    private static final int PLACEHOLDER_PAIRS = 17;

    public DashboardPeriodSummary getSummary(LocalDate from, LocalDate to) throws DaoException {
        Date fromSql = Date.valueOf(from);
        Date toSql = Date.valueOf(to);
        Object[] params = new Object[PLACEHOLDER_PAIRS * 2];
        for (int i = 0; i < params.length; i += 2) {
            params[i] = fromSql;
            params[i + 1] = toSql;
        }
        DashboardPeriodSummary summary = queryForObject(SQL, this::map, params);
        // The query has no FROM/WHERE/GROUP BY at the top level, so it always produces
        // exactly one row - queryForObject only returns null when a row count other than
        // one came back, which would mean this assumption stopped holding. Fail loudly
        // rather than let every caller's unconditional summary.getX() turn that into an NPE.
        if (summary == null) {
            throw new DaoException("تعذر حساب ملخص لوحة المؤشرات للفترة المحددة");
        }
        return summary;
    }

    @Override
    public DashboardPeriodSummary map(ResultSet rs) throws DaoException {
        try {
            return new DashboardPeriodSummary(
                    rs.getLong("sales_count"),
                    rs.getBigDecimal("sales_total"),
                    rs.getLong("purchases_count"),
                    rs.getBigDecimal("purchases_total"),
                    rs.getBigDecimal("total_receipts"),
                    rs.getBigDecimal("total_payments_and_expenses"),
                    rs.getBigDecimal("total_discounts")
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
