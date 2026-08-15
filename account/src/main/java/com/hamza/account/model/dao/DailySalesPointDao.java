package com.hamza.account.model.dao;

import com.hamza.account.model.domain.DailySalesPoint;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily sales totals for the dashboard trend chart. Reads {@code total_sales} directly
 * rather than {@code daily_dashboard_report}, which only ever holds today/week/month
 * aggregates and has no per-day breakdown.
 */
public class DailySalesPointDao extends AbstractDao<DailySalesPoint> {

    public DailySalesPointDao() {
        super();
    }

    public List<DailySalesPoint> getSalesTrend(int days) throws DaoException {
        String query = "SELECT DATE(invoice_date) AS d, SUM(total) AS t " +
                "FROM total_sales " +
                "WHERE invoice_date >= ? " +
                "GROUP BY DATE(invoice_date) " +
                "ORDER BY d";
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        return queryForObjects(query, this::map, Date.valueOf(from));
    }

    @Override
    public DailySalesPoint map(ResultSet rs) throws DaoException {
        try {
            return new DailySalesPoint(rs.getDate("d").toLocalDate(), rs.getBigDecimal("t"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
