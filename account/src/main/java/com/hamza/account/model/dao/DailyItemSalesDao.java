package com.hamza.account.model.dao;

import com.hamza.account.model.domain.DailyItemSales;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DailyItemSalesDao extends AbstractDao<DailyItemSales> {

    public DailyItemSalesDao(Connection connection) {
        super(connection);
    }

    // الدالة الرئيسية لجلب مبيعات اليوم
    public List<DailyItemSales> getDailyItemsReport(LocalDate date) throws DaoException {
        // الاستعلام من الـ View التي تحتوي على كل تفاصيل المبيعات
        String query = "SELECT nameItem AS item_name, price, " +
                "SUM(quantity) AS quantity, SUM(total_sales) AS total " +
                "FROM sales_names_table " +
                "WHERE DATE(invoice_date) = ? " +
                "GROUP BY nameItem, price " +
                "ORDER BY total DESC";

        // تحويل LocalDate الخاص بـ Java إلى java.sql.Date ليقبله JDBC
        return queryForObjects(query, this::map, java.sql.Date.valueOf(date));
    }

    @Override
    public DailyItemSales map(ResultSet rs) throws DaoException {
        DailyItemSales model = new DailyItemSales();
        try {
            model.setItemName(rs.getString("item_name"));
            model.setPrice(rs.getDouble("price"));
            model.setQuantity(rs.getDouble("quantity"));
            model.setTotal(rs.getDouble("total"));
//            model.setInvoiceNumber(rs.getString("invoice_number"));

            // استخراج الوقت من حقل التاريخ (مثال: 14:30) ليعرف المدير متى تم البيع
//            java.sql.Timestamp timestamp = rs.getTimestamp("invoice_date");
//            if (timestamp != null) {
//                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
//                model.setInvoiceTime(timestamp.toLocalDateTime().format(timeFormatter));
//            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }

        return model;
    }
}
