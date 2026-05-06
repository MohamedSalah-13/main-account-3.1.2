package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TopSellingItemDao extends AbstractDao<TopSellingItem> {

    public TopSellingItemDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TopSellingItem> loadAll() throws DaoException {
        return queryForObjects("SELECT * FROM top_selling_items_current_month", this::map);
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
