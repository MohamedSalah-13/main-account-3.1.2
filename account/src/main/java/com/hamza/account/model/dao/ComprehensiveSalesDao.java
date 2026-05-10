package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ComprehensiveSalesReport;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ComprehensiveSalesDao extends AbstractDao<ComprehensiveSalesReport> {

    public ComprehensiveSalesDao(Connection connection) {
        super(connection);
    }

    // دالة البحث بين تاريخين (يومي أو فترة)
    public List<ComprehensiveSalesReport> getSalesByPeriod(LocalDate fromDate, LocalDate toDate) throws DaoException {
        String query = """
                SELECT ts.invoice_number, ts.invoice_date,
                                COALESCE(c.name, 'عميل نقدي') AS customer_name,
                                ts.total AS gross_total, ts.discount,
                                (ts.total - ts.discount) AS net_total, ts.paid_up, (ts.total - ts.discount) - ts.paid_up AS amount
                                FROM total_sales ts
                                LEFT JOIN custom c ON ts.sup_code = c.id
                                WHERE DATE(ts.invoice_date) BETWEEN ? AND ?
                                ORDER BY ts.invoice_date DESC""";

        return queryForObjects(query, this::map,
                java.sql.Date.valueOf(fromDate),
                java.sql.Date.valueOf(toDate));
    }

    @Override
    public ComprehensiveSalesReport map(ResultSet rs) throws DaoException {
        ComprehensiveSalesReport model = new ComprehensiveSalesReport();
        try {
            model.setInvoiceNumber(rs.getString("invoice_number"));

            java.sql.Timestamp timestamp = rs.getTimestamp("invoice_date");
            if (timestamp != null) {
                model.setInvoiceDate(timestamp.toLocalDateTime());
            }

            model.setCustomerName(rs.getString("customer_name"));
            model.setGrossTotal(rs.getDouble("gross_total"));
            model.setDiscount(rs.getDouble("discount"));
            model.setNetTotal(rs.getDouble("net_total"));
            model.setPayed(rs.getDouble("paid_up"));
            model.setRemain(rs.getDouble("amount"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }

        return model;
    }

    // باقي الدوال (insert, update, delete) غير مدعومة لأن هذا تقرير
}