package com.hamza.account.model.dao;

import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.account.model.domain.TreasuryTransferModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class TreasuryTransferDao extends AbstractDao<TreasuryTransferModel> {

    private final String TABLE_NAME = "treasury_transfers";
    private final String TABLE_VIEW = "treasury_transfers_and_names";
    private final String ID = "id";
    private final String TREASURY_FROM = "treasury_from";
    private final String TREASURY_TO = "treasury_to";
    private final String TRANSFER_DATE = "transfer_date";
    private final String NOTES = "notes";
    private final String AMOUNT = "amount";
    private final String TREASURY_NAME_FROM = "treasury_name_from";
    private final String TREASURY_NAME_TO = "treasury_name_to";
    private final String USER_ID = "user_id";

    protected TreasuryTransferDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<TreasuryTransferModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_VIEW), this::map);
    }

    @Override
    public int insert(TreasuryTransferModel treasuryModel) throws DaoException {
        Object[] objects = {treasuryModel.getTreasuryFrom().getId(), treasuryModel.getTreasuryTo().getId()
                , treasuryModel.getAmount(), treasuryModel.getDate(), treasuryModel.getNotes(), treasuryModel.getUsers().getId()};
        String insert = SqlStatements.insertStatement(TABLE_NAME, TREASURY_FROM, TREASURY_TO, AMOUNT, TRANSFER_DATE
                , NOTES, USER_ID);
        return executeUpdate(insert, objects);
    }

    @Override
    public int update(TreasuryTransferModel unitsModel) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, ID, TREASURY_FROM, TREASURY_TO, AMOUNT, TRANSFER_DATE, NOTES);
        return executeUpdate(update, getData(unitsModel));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, ID);
        return executeUpdate(deleteStatement, id);
    }

    @Override
    public TreasuryTransferModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }


    @Override
    public Object[] getData(TreasuryTransferModel model) throws DaoException {
        return new Object[]{model.getTreasuryFrom().getId(), model.getTreasuryTo().getId()
                , model.getAmount(), model.getDate(), model.getNotes(), model.getId()};
    }

    @Override
    public TreasuryTransferModel map(ResultSet rs) throws DaoException {
        TreasuryTransferModel unitsModel = new TreasuryTransferModel();
        try {
            String stringNameFrom = rs.getString(TREASURY_NAME_FROM);
            String stringNameTo = rs.getString(TREASURY_NAME_TO);

            unitsModel.setId(rs.getInt(ID));
            unitsModel.setTreasuryFrom(new TreasuryModel(rs.getInt(TREASURY_FROM), stringNameFrom, 0));
            unitsModel.setTreasuryTo(new TreasuryModel(rs.getInt(TREASURY_TO), stringNameTo, 0));
            unitsModel.setAmount(rs.getDouble(AMOUNT));
            unitsModel.setDate(LocalDate.parse(rs.getString(TRANSFER_DATE)));
            unitsModel.setNotes(rs.getString(NOTES));
            unitsModel.setTreasuryNameTo(stringNameTo);
            unitsModel.setTreasuryNameFrom(stringNameFrom);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return unitsModel;
    }
}
