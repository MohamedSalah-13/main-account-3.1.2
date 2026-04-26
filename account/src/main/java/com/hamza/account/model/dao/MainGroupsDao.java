package com.hamza.account.model.dao;

import com.hamza.account.model.domain.MainGroups;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MainGroupsDao extends AbstractDao<MainGroups> {


    private final String TABLE_NAME = "main_group";
    private final String ID = "id";
    private final String NAME_G = "name_g";
    private final String USER_ID = "user_id";

    MainGroupsDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<MainGroups> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(MainGroups mainGroupModel) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, NAME_G, USER_ID), mainGroupModel.getName(), mainGroupModel.getUsers().getId());
    }

    @Override
    public int update(MainGroups mainGroups) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, NAME_G), mainGroups.getName(), mainGroups.getId());
    }

    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid main group ID: " + id);
        if (id == 1)
            throw new IllegalArgumentException("Cannot delete main group with ID 1");
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public MainGroups getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public MainGroups getDataByString(String name) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, NAME_G), this::map, name);
    }

    @Override
    public Object[] getData(MainGroups mainGroups) {
        return new Object[]{mainGroups.getName()};
    }

    @Override
    public MainGroups map(ResultSet resultSet) throws DaoException {
        MainGroups mainGroups = new MainGroups();
        try {
            mainGroups.setId(resultSet.getInt(ID));
            mainGroups.setName(resultSet.getString(NAME_G));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return mainGroups;
    }
}
