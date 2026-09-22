package com.hamza.account.model.dao;

import com.hamza.account.document.ItemNetLines;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The dashboard's five best sellers over a period.
 *
 * <p>It used to sum {@code sales.quantity} as stored, so two cartons of twelve and three single pieces
 * came to five of one thing, and its average price was the carton's and the piece's prices averaged
 * together; returns were not subtracted at all. It reads {@link ItemNetLines} now: base units, net of
 * returns, the delegate report's amount per line.</p>
 */
public class TopSellingItemDao extends AbstractDao<TopSellingItem> {

    static final String TOP_SELLING_BETWEEN_DATES_SQL = """
            SELECT i.nameItem AS item_name,
                   COALESCE(u.unit_name, '') AS unit_name,
                   SUM(m.quantity) AS total_quantity,
                   CAST(SUM(m.amount - m.returned_amount) / SUM(m.quantity) AS DECIMAL(14,2)) AS average_price
            FROM (%s) m
                     JOIN items i ON i.id = m.item_id
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            GROUP BY i.id, i.nameItem, u.unit_name
            HAVING SUM(m.quantity) > 0
            ORDER BY total_quantity DESC, item_name
            LIMIT 5
            """.formatted(ItemNetLines.SALES.perItemSql(false));

    public TopSellingItemDao() {
        super();
    }

    /** The five items that sold most, in base units, over an inclusive [from, to]. */
    public List<TopSellingItem> getTopSellingItems(LocalDate from, LocalDate to) throws DaoException {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to);
        return queryForObjects(TOP_SELLING_BETWEEN_DATES_SQL, this::map, start, end, start, end);
    }

    @Override
    public TopSellingItem map(ResultSet rs) throws DaoException {
        try {
            return new TopSellingItem(
                    rs.getString("item_name"),
                    rs.getBigDecimal("total_quantity"),
                    rs.getBigDecimal("average_price"),
                    rs.getString("unit_name"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
