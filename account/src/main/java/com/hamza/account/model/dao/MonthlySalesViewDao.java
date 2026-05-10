package com.hamza.account.model.dao; // نفس الباكدج الخاصة بك

import com.hamza.account.model.domain.MonthlySalesViewModel; // تأكد من مسار الكلاس الخاص بالـ Model
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MonthlySalesViewDao extends AbstractDao<MonthlySalesViewModel> {

    private final String SALES_YEAR = "sales_year";
    private final String JANUARY = "January";
    private final String FEBRUARY = "February";
    private final String MARCH = "March";
    private final String APRIL = "April";
    private final String MAY = "May";
    private final String JUNE = "June";
    private final String JULY = "July";
    private final String AUGUST = "August";
    private final String SEPTEMBER = "September";
    private final String OCTOBER = "October";
    private final String NOVEMBER = "November";
    private final String DECEMBER = "December";
    private final String TOTAL_YEARLY_SALES = "total_yearly_sales";
    //    private final String TABLE_NAME = "view_monthly_sales";
    private String TABLE_NAME;

    public MonthlySalesViewDao(Connection connection, String tableName) {
        super(connection);
        TABLE_NAME = tableName;
    }

    @Override
    public List<MonthlySalesViewModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public MonthlySalesViewModel getDataById(int year) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, SALES_YEAR), this::map, year);
    }

    @Override
    public MonthlySalesViewModel map(ResultSet rs) throws DaoException {
        MonthlySalesViewModel model;
        try {
            model = new MonthlySalesViewModel();

            // قراءة السنة
            model.setSalesYear(rs.getInt(SALES_YEAR));

            // قراءة شهور السنة (باستخدام BigDecimal كما اتفقنا سابقاً للدقة المالية)
            model.setJanuary(rs.getBigDecimal(JANUARY));
            model.setFebruary(rs.getBigDecimal(FEBRUARY));
            model.setMarch(rs.getBigDecimal(MARCH));
            model.setApril(rs.getBigDecimal(APRIL));
            model.setMay(rs.getBigDecimal(MAY));
            model.setJune(rs.getBigDecimal(JUNE));
            model.setJuly(rs.getBigDecimal(JULY));
            model.setAugust(rs.getBigDecimal(AUGUST));
            model.setSeptember(rs.getBigDecimal(SEPTEMBER));
            model.setOctober(rs.getBigDecimal(OCTOBER));
            model.setNovember(rs.getBigDecimal(NOVEMBER));
            model.setDecember(rs.getBigDecimal(DECEMBER));

            // قراءة الإجمالي
            model.setTotalYearlySales(rs.getBigDecimal(TOTAL_YEARLY_SALES));

        } catch (SQLException e) {
            // استخدام الـ DaoException الخاص بمشروعك
            throw new DaoException(e.getMessage(), e);
        }
        return model;
    }
}