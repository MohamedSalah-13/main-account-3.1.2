package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TreasuryBalance;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import com.hamza.controlsfx.util.NumberUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TreasuryBalanceDao extends AbstractDao<TreasuryBalance> {

    private final String TABLE_NAME = "treasury_balance";
    private final String ID = "id_no";
    private final String DATE_VAL = "date_val";
    private final String INCOME = "income";
    private final String OUTPUT = "output";
    private final String TREASURY_ID = "treasury_id";
    private final String DATE_INSERT = "date_insert";
    private final String USER_ID = "user_id";
    private final String USER_NAME = "user_name";
    private final String INFORMATION = "information";
    private final String TREASURY_NAME = "treasury_name";

    protected TreasuryBalanceDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TreasuryBalance> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public TreasuryBalance map(ResultSet rs) throws DaoException {
        TreasuryBalance treasuryBalance = new TreasuryBalance();
        try {
            treasuryBalance.setId(rs.getInt(ID));
            treasuryBalance.setName(rs.getString(TREASURY_NAME));
            var income = rs.getDouble(INCOME);
            var output = rs.getDouble(OUTPUT);
            treasuryBalance.setTotal_income(NumberUtils.roundToTwoDecimalPlaces(income));
            treasuryBalance.setTotal_output(NumberUtils.roundToTwoDecimalPlaces(output));
            treasuryBalance.setBalance(NumberUtils.roundToTwoDecimalPlaces(income - output));
            treasuryBalance.setTreasury_id(rs.getInt(TREASURY_ID));
            treasuryBalance.setDate(rs.getDate(DATE_VAL).toLocalDate());
            treasuryBalance.setInformation(rs.getString(INFORMATION));
            treasuryBalance.setUser_id(rs.getInt(USER_ID));
            treasuryBalance.setUser_name(rs.getString(USER_NAME));
            treasuryBalance.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return treasuryBalance;
    }

    public List<TreasuryBalance> loadAllBetweenTwoData(String fromDate, String toDate) throws DaoException {
        String query = "SELECT * FROM treasury_balance WHERE date_val BETWEEN ? AND ?";
        return queryForObjects(query, this::map, fromDate, toDate);
    }

    public List<TreasuryBalance> getSumTreasuryBalance() throws DaoException {
        String query = "SELECT treasury_id,treasury_name ,SUM(income) AS income, SUM(output) AS output FROM treasury_balance GROUP BY treasury_id ,treasury_name";
        return queryForObjects(query, rs -> {
            TreasuryBalance treasuryBalance = new TreasuryBalance();
            treasuryBalance.setName(rs.getString(TREASURY_NAME));
            var income = rs.getDouble(INCOME);
            var output = rs.getDouble(OUTPUT);
            treasuryBalance.setTotal_income(NumberUtils.roundToTwoDecimalPlaces(income));
            treasuryBalance.setTotal_output(NumberUtils.roundToTwoDecimalPlaces(output));
            treasuryBalance.setBalance(NumberUtils.roundToTwoDecimalPlaces(income - output));
            treasuryBalance.setTreasury_id(rs.getInt(TREASURY_ID));
            return treasuryBalance;
        });
    }
}
