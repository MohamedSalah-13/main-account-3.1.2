package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TableDataReports;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class TableDataReportsDao extends AbstractDao<TableDataReports> {
    public TableDataReportsDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TableDataReports> loadAllById(int id) throws DaoException {
        return queryForObjects("select * from view_yearly_monthly_report where report_year = ?", this::map, id);
    }

    @Override
    public TableDataReports map(ResultSet rs) throws DaoException {
        TableDataReports tableDataReports=new TableDataReports();
        try {
            tableDataReports.setReport_month_name(Month.of(rs.getInt("report_month")).getDisplayName(TextStyle.FULL, Locale.getDefault()));
            tableDataReports.setReport_year(rs.getInt("report_year"));
            tableDataReports.setReport_month(rs.getInt("report_month"));
            tableDataReports.setPurchase(rs.getDouble("purchases"));
            tableDataReports.setPurchases_discount(rs.getDouble("purchases_discount"));
            tableDataReports.setSales(rs.getDouble("sales"));
            tableDataReports.setSales_discount(rs.getDouble("sales_discount"));
            tableDataReports.setPurchases_return(rs.getDouble("purchases_return"));
            tableDataReports.setPurchases_return_discount(rs.getDouble("purchases_return_discount"));
            tableDataReports.setSales_return(rs.getDouble("sales_return"));
            tableDataReports.setSales_return_discount(rs.getDouble("sales_return_discount"));
            tableDataReports.setExpense(rs.getDouble("expenses"));
            tableDataReports.setProfit(rs.getDouble("estimated_net_profit"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }

        return tableDataReports;
    }
}
