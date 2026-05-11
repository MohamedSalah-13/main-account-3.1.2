package com.hamza.account.model.dao;

import com.hamza.account.model.domain.AddDeposit;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.OperationType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class DepositDao extends AbstractDao<AddDeposit> {

    private final String TABLE_NAME = "treasury_deposit_expenses";
    private final String ID = "id";
    private final String STATEMENT = "statement";
    private final String DATE_INTER = "date_inter";
    private final String AMOUNT = "amount";
    private final String DESCRIPTION_DATA = "description_data";
    private final String DEPOSIT_OR_EXPENSES = "deposit_or_expenses";
    private final String TREASURY_ID = "treasury_id";
    private final String USER_ID = "user_id";

    DepositDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
    }

    @Override
    public List<AddDeposit> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(AddDeposit addDeposit) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, STATEMENT, DATE_INTER, AMOUNT, DESCRIPTION_DATA, DEPOSIT_OR_EXPENSES, TREASURY_ID, USER_ID)
                , addDeposit.getStatement(), addDeposit.getDate(), addDeposit.getAmount(), addDeposit.getDescription_data(), addDeposit.getOperationType().getId()
                , addDeposit.getTreasury().getId(), addDeposit.getUsers().getId());
    }

    @Override
    public int update(AddDeposit addDeposit) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, STATEMENT, DATE_INTER, AMOUNT, DESCRIPTION_DATA, DEPOSIT_OR_EXPENSES, TREASURY_ID)
                , addDeposit.getStatement(), addDeposit.getDate(), addDeposit.getAmount(), addDeposit.getDescription_data(), addDeposit.getOperationType().getId()
                , addDeposit.getTreasury().getId(), addDeposit.getId());
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public AddDeposit getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public AddDeposit map(ResultSet resultSet) throws DaoException {
        AddDeposit addDeposit = new AddDeposit();
        try {
            addDeposit.setId(resultSet.getInt(ID));
            addDeposit.setAmount(resultSet.getDouble(AMOUNT));
            addDeposit.setDate(LocalDate.parse(resultSet.getString(DATE_INTER)));
            addDeposit.setStatement(resultSet.getString(STATEMENT));
            addDeposit.setOperationType(resultSet.getInt(DEPOSIT_OR_EXPENSES) == 1 ? OperationType.DEPOSIT : OperationType.EXCHANGE);
            addDeposit.setDescription_data(resultSet.getString(DESCRIPTION_DATA));
//            addDeposit.setTreasury(daoFactory.treasuryDao().getDataById(resultSet.getInt(TREASURY_ID)));
            addDeposit.setTreasury(new Treasury(resultSet.getInt(TREASURY_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return addDeposit;
    }
}
