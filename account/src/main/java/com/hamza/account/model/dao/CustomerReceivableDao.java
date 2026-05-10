package com.hamza.account.model.dao;

import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CustomerReceivableDao extends AbstractDao<CustomerReceivable> {

    public CustomerReceivableDao(Connection connection) {
        super(connection);
    }

    public List<CustomerReceivable> getReceivablesReport() throws DaoException {
        String query = "SELECT * FROM view_customer_receivables where final_balance > 0 ORDER BY customer_name";
        return queryForObjects(query, this::map);
    }

    @Override
    public CustomerReceivable map(ResultSet rs) throws DaoException {
        CustomerReceivable model = new CustomerReceivable();
        try {
            model.setCustomerId(rs.getInt("customer_id"));
            model.setCustomerName(rs.getString("customer_name"));
            model.setCustomerPhone(rs.getString("customer_phone"));
            model.setInvoicesDebt(rs.getDouble("total_invoices_debt"));
            model.setOpeningBalance(rs.getDouble("opening_balance"));
            model.setTotalPayments(rs.getDouble("total_payments"));
            model.setTotalReceivable(rs.getDouble("final_balance"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }
}