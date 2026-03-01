package com.hamza.account.model.dao;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

@Log4j2
public class TruncateDao extends AbstractDao {

    private final Connection connection;

    public TruncateDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    public void truncateTableSales(boolean salesReturn, boolean deleteSales, boolean deleteAccount, boolean deleteName) throws DaoException {
        try {
            CallableStatement cs = connection.prepareCall("{CALL truncateTableSales(" + salesReturn + "," + deleteSales + "," + deleteAccount + "," + deleteName + ")}");
            cs.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public void truncateTablePurchase(boolean deletePurchaseReturn, boolean deletePurchase, boolean deleteAccount, boolean deleteName) throws DaoException {
        try {
            CallableStatement cs = connection.prepareCall("{CALL truncateTablePurchase(" + deletePurchaseReturn + "," + deletePurchase + "," + deleteAccount + "," + deleteName + ")}");
            cs.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public void truncateTableItems(boolean deleteItems, boolean deleteStock, boolean deleteSubGroup, boolean deleteMainGroup) throws DaoException {
        try {
            CallableStatement cs = connection.prepareCall("{CALL truncateTableItems(" + deleteItems + "," + deleteStock + "," + deleteSubGroup + "," + deleteMainGroup + ")}");
            cs.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public void truncateTableOthers(boolean deleteEmployees, boolean deleteProcesses, boolean deleteExpenses, boolean deleteUsers) throws DaoException {
        try {
            CallableStatement cs = connection.prepareCall("{CALL truncateTableOthers(" + deleteEmployees + "," + deleteProcesses + "," + deleteExpenses + "," + deleteUsers + ")}");
            cs.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
