package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TargetsDetails;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class TargetDetailsDao extends AbstractDao<TargetsDetails> {

    private final String TARGET_DELEGATE = "target_delegate";
    private final String EMPLOYEE_ID = "employee_id";
    private final String EMPLOYEE_NAME = "employee_name";
    private final String TOTALS = "total_sales_sum";
    private final String TOTALS_RETURN = "total_sales_re_sum";
    private final String AMOUNT = "Amount";
    private final String TARGET_RATIO_1 = "target_ratio1";
    private final String TARGET_RATIO_2 = "target_ratio2";
    private final String TARGET_RATIO_3 = "target_ratio3";
    private final String RATE_1 = "rate_1";
    private final String RATE_2 = "rate_2";
    private final String RATE_3 = "rate_3";
    private final String TARGET = "target";
    private final String SALES_YEAR = "sales_year";
    private final String SALES_MONTH = "sales_month";
    private final String COMMISSION_MONTH = "commission";

    public TargetDetailsDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TargetsDetails> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TARGET_DELEGATE), this::map);
    }

    @Override
    public TargetsDetails map(ResultSet rs) throws DaoException {
        TargetsDetails target = new TargetsDetails();
        try {
            double totalSalesSum = rs.getDouble(TOTALS);
            double totalSalesSumTarget = rs.getDouble(TOTALS_RETURN);

            target.setTotals_amount(rs.getDouble(AMOUNT));
            target.setEmployee_id(rs.getInt(EMPLOYEE_ID));
            target.setEmployee_name(rs.getString(EMPLOYEE_NAME));
            target.setTotals_sales_sum(totalSalesSum);
            target.setTotals_sales_re_sum(totalSalesSumTarget);
            target.setTarget_ratio1(rs.getDouble(TARGET_RATIO_1));
            target.setTarget_ratio2(rs.getDouble(TARGET_RATIO_2));
            target.setTarget_ratio3(rs.getDouble(TARGET_RATIO_3));
            target.setRate1(rs.getDouble(RATE_1));
            target.setRate2(rs.getDouble(RATE_2));
            target.setRate3(rs.getDouble(RATE_3));
            target.setTarget(rs.getDouble(TARGET));
            target.setSales_year(rs.getInt(SALES_YEAR));
            target.setSales_month(rs.getInt(SALES_MONTH));
            target.setCommission_month(rs.getDouble(COMMISSION_MONTH));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return target;
    }
}
