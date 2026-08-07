package com.hamza.account.model.dao;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.sql.CallableStatement;
import java.sql.SQLException;

@Log4j2
public class TruncateDao extends AbstractDao {

    public TruncateDao() {
        super();
    }

    public void truncateTableSales(boolean salesReturn, boolean deleteSales, boolean deleteAccount, boolean deleteName) throws DaoException {
        call("{CALL truncateTableSales(" + salesReturn + "," + deleteSales + "," + deleteAccount + "," + deleteName + ")}");
    }

    public void truncateTablePurchase(boolean deletePurchaseReturn, boolean deletePurchase, boolean deleteAccount, boolean deleteName) throws DaoException {
        call("{CALL truncateTablePurchase(" + deletePurchaseReturn + "," + deletePurchase + "," + deleteAccount + "," + deleteName + ")}");
    }

    public void truncateTableItems(boolean deleteItems, boolean deleteStock, boolean deleteSubGroup, boolean deleteMainGroup) throws DaoException {
        call("{CALL truncateTableItems(" + deleteItems + "," + deleteStock + "," + deleteSubGroup + "," + deleteMainGroup + ")}");
    }

    public void truncateTableOthers(boolean deleteEmployees, boolean deleteProcesses, boolean deleteExpenses, boolean deleteUsers) throws DaoException {
        call("{CALL truncateTableOthers(" + deleteEmployees + "," + deleteProcesses + "," + deleteExpenses + "," + deleteUsers + ")}");
    }

    /**
     * Each of these used to leave its CallableStatement open; closing it also
     * releases the server-side cursor rather than waiting for the connection to be
     * discarded.
     */
    private void call(String sql) throws DaoException {
        withConnection(connection -> {
            try (CallableStatement cs = connection.prepareCall(sql)) {
                cs.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        });
    }
}
