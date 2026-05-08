package com.hamza.account.model.dao;

import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CustomerPurchasedItemDao extends AbstractDao<CustomerPurchasedItem> {

    public CustomerPurchasedItemDao(Connection connection) {
        super(connection);
    }

    @Override
    public CustomerPurchasedItem map(ResultSet rs) throws DaoException {
       CustomerPurchasedItem customerPurchasedItem = new CustomerPurchasedItem();
        try {
            customerPurchasedItem.setCustomerId(rs.getInt("customer_id"));
            customerPurchasedItem.setCustomerName(rs.getString("customer_name"));
            customerPurchasedItem.setItemName(rs.getString("item_name"));
            customerPurchasedItem.setQuantity(rs.getBigDecimal("quantity"));
            customerPurchasedItem.setSellingPrice(rs.getBigDecimal("selling_price"));
            customerPurchasedItem.setInvoiceDate(rs.getDate("invoice_date").toLocalDate());
            customerPurchasedItem.setInvoiceNumber(rs.getLong("invoice_number"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }

        return customerPurchasedItem;
    }

    public List<CustomerPurchasedItem> findByCustomerId(int customerId) throws DaoException {
        return queryForObjects("SELECT * FROM view_customer_purchased_items WHERE customer_id = ?", this::map, customerId);
    }
}
