package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Earnings;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.text.NumberUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class EarningsDao extends AbstractDao<Earnings> {

    private final String TABLE_VIEW = "earnings_reports";
    private final String ID = "id";
    private final String CODE_ID = "code_id";
    private final String INVOICE_DATE = "invoice_date";
    private final String INVOICE_TYPE = "invoice_type";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    private final String PAID = "paid_up";
    private final String STOCK_ID = "stock_id";
    private final String DELEGATE_ID = "delegate_id";
    private final String TREASURY_ID = "treasury_id";
    private final String DATE_INSERT = "date_insert";
    private final String USER_ID = "user_id";
    private final String PROFIT = "profit";
    private final String TABLE_NAME = "table_name";

    public EarningsDao(Connection connection) {
        super(connection);
    }

  /*  @Override
    public List<Earnings> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public Earnings map(ResultSet rs) throws DaoException {
        Earnings earnings = new Earnings();
        try {
            earnings.setId(rs.getInt(ID));
            earnings.setCode_id(rs.getInt(CODE_ID));
            earnings.setInvoice_date(LocalDate.parse(rs.getString(INVOICE_DATE)));
            earnings.setInvoice_type(rs.getInt(INVOICE_TYPE));
            earnings.setTotal(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(TOTAL)));
            earnings.setDiscount(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(DISCOUNT)));
            earnings.setPaid(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(PAID)));
            earnings.setStock_id(rs.getInt(STOCK_ID));
            earnings.setDelegate_id(rs.getInt(DELEGATE_ID));
            earnings.setTreasury_id(rs.getInt(TREASURY_ID));
            earnings.setDate_insert(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            earnings.setUsers(new Users(rs.getInt(USER_ID)));
            earnings.setTable_id(rs.getString(TABLE_NAME));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return earnings;
    }*/

    public Earnings mapTotals(ResultSet rs) throws DaoException {
        Earnings earnings = new Earnings();
        try {
            earnings.setTotal(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(TOTAL)));
            earnings.setDiscount(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(DISCOUNT)));
            earnings.setPaid(NumberUtils.roundToTwoDecimalPlaces(rs.getDouble(PAID)));
            earnings.setTable_id(rs.getString(TABLE_NAME));
            earnings.setProfit(rs.getDouble(PROFIT));
            earnings.setUsers(new Users(rs.getInt(USER_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return earnings;
    }

    public List<Earnings> getEarningsByDateRange(LocalDate startDate, LocalDate endDate) throws DaoException {
        final String QUERY_WITH_DATE_FILTER = """
                SELECT %s,%s, SUM(%s) AS total , SUM(%s) AS discount, SUM(%s) AS paid_up , SUM(%s) AS profit
                FROM %s
                WHERE %s BETWEEN ? AND ?
                GROUP BY %s ,%s""".formatted(
                USER_ID, TABLE_NAME, TOTAL, DISCOUNT, PAID, PROFIT, TABLE_VIEW, INVOICE_DATE, USER_ID, TABLE_NAME);

        final String QUERY_WITHOUT_DATE_FILTER = """
                SELECT %s,%s, SUM(%s) AS total , SUM(%s) AS discount, SUM(%s) AS paid_up ,SUM(%s) AS profit
                FROM %s
                GROUP BY %s,%s""".formatted(
                USER_ID, TABLE_NAME, TOTAL, DISCOUNT, PAID, PROFIT, TABLE_VIEW, USER_ID, TABLE_NAME);

        String query = (startDate == null || endDate == null) ? QUERY_WITHOUT_DATE_FILTER : QUERY_WITH_DATE_FILTER;
        return queryForObjects(query, this::mapTotals, startDate, endDate);
    }
}
