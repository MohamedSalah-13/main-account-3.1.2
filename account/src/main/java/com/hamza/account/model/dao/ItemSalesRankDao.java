package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemSalesRank;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ItemSalesRankDao extends AbstractDao<ItemSalesRank> {

    public ItemSalesRankDao(Connection connection) {
        super(connection);
    }

    // جلب تقرير حسب السنة والشهر (تقرير شهري)
    public List<ItemSalesRank> getBestSellersByMonth(int year, int month) throws DaoException {
        String query = "SELECT * FROM view_item_sales_rank WHERE sales_year = ? AND sales_month = ? ORDER BY total_qty DESC";
        return queryForObjects(query, this::map, year, month);
    }

    // جلب تقرير حسب السنة فقط (تقرير سنوي)
    public List<ItemSalesRank> getBestSellersByYear(int year) throws DaoException {
        String query = "SELECT item_id, item_name, SUM(total_qty) as total_qty, SUM(total_amount) as total_amount, " +
                "SUM(total_profit) as total_profit FROM view_item_sales_rank " +
                "WHERE sales_year = ? GROUP BY item_id, item_name ORDER BY total_qty DESC";
        return queryForObjects(query, this::map, year);
    }

    @Override
    public ItemSalesRank map(ResultSet rs) throws DaoException {
        ItemSalesRank model = new ItemSalesRank();
        try {
            model.setItemId(rs.getInt("item_id"));
            model.setItemName(rs.getString("item_name"));
            model.setTotalQty(rs.getDouble("total_qty"));
            model.setTotalAmount(rs.getDouble("total_amount"));
            model.setTotalProfit(rs.getDouble("total_profit"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }

}

