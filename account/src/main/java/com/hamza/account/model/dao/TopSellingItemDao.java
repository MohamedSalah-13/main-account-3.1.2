package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class TopSellingItemDao extends AbstractDao<TopSellingItem> {

    private static final String TOP_SELLING_BETWEEN_DATES_SQL = """
            SELECT i.nameItem AS item_name,
                   SUM(s.quantity) AS total_quantity,
                   CAST((SUM(s.total_sel_price) / SUM(s.quantity)) AS DECIMAL(14,2)) AS average_price
            FROM sales s
                     JOIN total_sales ts ON s.invoice_number = ts.invoice_number
                     JOIN items i ON s.num = i.id
            WHERE ts.invoice_date BETWEEN ? AND ?
            GROUP BY i.id, i.nameItem
            ORDER BY total_quantity DESC
            LIMIT 5
            """;

    public TopSellingItemDao() {
        super();
    }

    @Override
    public List<TopSellingItem> loadAll() throws DaoException {
        return queryForObjects("SELECT * FROM top_selling_items_current_month", this::map);
    }

    /**
     * The same ranking {@code top_selling_items_current_month} computes, over an
     * arbitrary inclusive [from, to] range instead of the current calendar month.
     */
    public List<TopSellingItem> getTopSellingItems(LocalDate from, LocalDate to) throws DaoException {
        return queryForObjects(TOP_SELLING_BETWEEN_DATES_SQL, this::map, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public TopSellingItem map(ResultSet rs) throws DaoException {
        try {
            return new TopSellingItem(
                    rs.getString("item_name"),
                    rs.getBigDecimal("total_quantity"),
                    rs.getBigDecimal("average_price")
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
