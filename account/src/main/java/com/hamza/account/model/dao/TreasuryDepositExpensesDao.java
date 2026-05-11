package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.TreasuryDepositExpenses;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

public class TreasuryDepositExpensesDao extends AbstractDao<TreasuryDepositExpenses> {

    private static final String TABLE_NAME = "treasury_deposit_expenses";

    private static final int DEPOSIT = 1;
    private static final int EXPENSES = 2;

    private final TreasuryDao treasuryDao;

    public TreasuryDepositExpensesDao(Connection connection) {
        super(connection);
        this.treasuryDao = new TreasuryDao(connection);
    }

    @Override
    public TreasuryDepositExpenses map(ResultSet rs) throws DaoException {
        try {
            TreasuryDepositExpenses item = new TreasuryDepositExpenses();

            item.setId(rs.getInt("id"));
            item.setStatement(rs.getString("statement"));

            var dateInter = rs.getDate("date_inter");
            if (dateInter != null) {
                item.setDateInter(dateInter.toLocalDate());
            }

            item.setAmount(rs.getBigDecimal("amount"));
            item.setDescriptionData(rs.getString("description_data"));
            item.setDepositOrExpenses(rs.getInt("deposit_or_expenses"));
            item.setUserId(rs.getInt("user_id"));

            Treasury treasury = new Treasury();
            treasury.setId(rs.getInt("treasury_id"));
            treasury.setName(rs.getString("t_name"));
            treasury.setAmount(rs.getBigDecimal("treasury_amount"));
            item.setTreasury(treasury);

            return item;
        } catch (Exception e) {
            throw new DaoException(e);
        }
    }

    @Override
    public List<TreasuryDepositExpenses> loadAll() throws DaoException {
        String query = """
                SELECT tde.id,
                       tde.statement,
                       tde.date_inter,
                       tde.amount,
                       tde.description_data,
                       tde.deposit_or_expenses,
                       tde.treasury_id,
                       tde.user_id,
                       tr.t_name,
                       tr.amount AS treasury_amount
                FROM treasury_deposit_expenses tde
                JOIN treasury tr ON tr.id = tde.treasury_id
                ORDER BY tde.date_inter DESC, tde.id DESC
                """;
        return queryForObjects(query, this::map);
    }

    public List<TreasuryDepositExpenses> loadBetweenDates(LocalDate startDate, LocalDate endDate) throws DaoException {
        String query = """
                SELECT tde.id,
                       tde.statement,
                       tde.date_inter,
                       tde.amount,
                       tde.description_data,
                       tde.deposit_or_expenses,
                       tde.treasury_id,
                       tde.user_id,
                       tr.t_name,
                       tr.amount AS treasury_amount
                FROM treasury_deposit_expenses tde
                JOIN treasury tr ON tr.id = tde.treasury_id
                WHERE tde.date_inter BETWEEN ? AND ?
                ORDER BY tde.date_inter DESC, tde.id DESC
                """;
        return queryForObjects(query, this::map, startDate, endDate);
    }

    public int addDeposit(TreasuryDepositExpenses item) throws DaoException {
        item.setDepositOrExpenses(DEPOSIT);
        return insertWithBalanceUpdate(item);
    }

    public int addExpenses(TreasuryDepositExpenses item) throws DaoException {
        item.setDepositOrExpenses(EXPENSES);
        return insertWithBalanceUpdate(item);
    }

    private int insertWithBalanceUpdate(TreasuryDepositExpenses item) throws DaoException {
        return insertMultiData(() -> {
            insertOnly(item);

            if (item.isDeposit()) {
                treasuryDao.increaseAmount(item.getTreasury().getId(), item.getAmount());
            } else if (item.isExpenses()) {
                int affectedRows = treasuryDao.decreaseAmount(item.getTreasury().getId(), item.getAmount());
                if (affectedRows == 0) {
                    throw new DaoException("رصيد الخزينة غير كافٍ");
                }
            }
        });
    }

    @Override
    public int insert(TreasuryDepositExpenses item) throws DaoException {
        return insertWithBalanceUpdate(item);
    }

    private int insertOnly(TreasuryDepositExpenses item) throws DaoException {
        String query = """
                INSERT INTO treasury_deposit_expenses
                    (statement, date_inter, amount, description_data, deposit_or_expenses, treasury_id, user_id)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?)
                """;

        return executeUpdate(
                query,
                item.getStatement(),
                item.getDateInter(),
                item.getAmount(),
                item.getDescriptionData(),
                item.getDepositOrExpenses(),
                item.getTreasury().getId(),
                item.getUserId()
        );
    }
}