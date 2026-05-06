package com.hamza.account.model.dao;

import com.hamza.account.model.domain.DailyDashboardReport;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DailyDashboardReportDao extends AbstractDao<DailyDashboardReport> {

    public DailyDashboardReportDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<DailyDashboardReport> loadAll() throws DaoException {
        return queryForObjects("select * from daily_dashboard_report", this::map);
    }

    @Override
    public DailyDashboardReport map(ResultSet rs) throws DaoException {
        DailyDashboardReport report = new DailyDashboardReport();
        try {
            report.setSalesCountToday(rs.getLong("sales_count_today"));
            report.setSalesTotalToday(rs.getBigDecimal("sales_total_today"));
            report.setSalesTotalYesterday(rs.getBigDecimal("sales_total_yesterday"));
            report.setSalesTotalWeek(rs.getBigDecimal("sales_total_week"));
            report.setSalesTotalMonth(rs.getBigDecimal("sales_total_month"));

            report.setPurchasesCountToday(rs.getLong("purchases_count_today"));
            report.setPurchasesTotalToday(rs.getBigDecimal("purchases_total_today"));

            report.setSalesReturnsCountToday(rs.getLong("sales_returns_count_today"));
            report.setSalesReturnsTotalToday(rs.getBigDecimal("sales_returns_total_today"));

            report.setPurchasesReturnsCountToday(rs.getLong("purchases_returns_count_today"));
            report.setPurchasesReturnsTotalToday(rs.getBigDecimal("purchases_returns_total_today"));

            report.setTotalReceiptsToday(rs.getBigDecimal("total_receipts_today"));
            report.setTotalPaymentsAndExpensesToday(rs.getBigDecimal("total_payments_and_expenses_today"));
            report.setTotalDiscountsToday(rs.getBigDecimal("total_discounts_today"));

        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return report;
    }
}
