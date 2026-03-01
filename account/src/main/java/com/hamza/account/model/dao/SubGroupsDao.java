package com.hamza.account.model.dao;

import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SubGroupsDao extends AbstractDao<SubGroups> {

    private final String ID = "id";
    private final String NAME = "name";
    private final String TABLE_NAME = "sub_group";
    private final String MAIN_ID = "main_id";
    private final String USER_ID = "user_id";
    private final DaoFactory daoFactory;

    SubGroupsDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<SubGroups> loadAll() throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_NAME);
        return queryForObjects(query, this::map);
    }

    @Override
    public List<SubGroups> loadAllById(int id) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID);
        return queryForObjects(query, this::map, id);
    }

    @Override
    public int insert(SubGroups mainGroupModel) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, NAME, MAIN_ID, USER_ID)
                , mainGroupModel.getName(), mainGroupModel.getMainGroups().getId(), mainGroupModel.getUsers().getId());
    }

    @Override
    public int update(SubGroups mainGroups) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, ID, NAME, MAIN_ID);
        return executeUpdate(update, getData(mainGroups));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public SubGroups getDataById(int id) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID);
        return queryForObject(query, this::map, id);
    }

    @Override
    public SubGroups getDataByString(String s) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, NAME);
        return queryForObject(query, this::map, s);
    }

    @Override
    public Object[] getData(SubGroups mainGroups) {
        return new Object[]{mainGroups.getName(), mainGroups.getMainGroups().getId(), mainGroups.getId()};
    }

    @Override
    public SubGroups map(ResultSet resultSet) throws DaoException {
        SubGroups subGroups = new SubGroups();
        try {
            subGroups.setId(resultSet.getInt(ID));
            subGroups.setName(resultSet.getString(NAME));
            int main_id = resultSet.getInt(MAIN_ID);
            MainGroups dataById = daoFactory.getMainGroups().getDataById(main_id);
            subGroups.setMainGroups(dataById);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return subGroups;
    }

    public SubGroups getDataByNameAndMainId(String name, int id) throws DaoException {
        String query = "select * from " + TABLE_NAME + " join main_group mg on sub_group." + MAIN_ID + " = mg." + ID + " where " + NAME + "=? and " + MAIN_ID + "=? ";
        return queryForObject(query, this::map, name, id);
    }
}
