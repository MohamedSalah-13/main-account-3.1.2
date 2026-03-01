package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UsersDao extends AbstractDao<Users> {

    public static final String USER_NAME = "user_name";
    private final String TABLE_NAME = "users";
    private final String ID = "id";
    private final String USER_PASS = "user_pass";
    private final String USER_ACTIVITY = "user_activity";
    private final String USER_AVAILABLE = "user_available";

    UsersDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Users> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Users users) throws DaoException {
        Object[] objects = {users.getUsername(), users.getPasswordHash()};
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, USER_NAME, USER_PASS), objects);
    }

    @Override
    public int update(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_NAME, USER_PASS, USER_ACTIVITY), getData(users));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Users getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Users getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, USER_NAME), this::map, s);
    }

    @Override
    public Object[] getData(Users users) {
        return new Object[]{users.getUsername(), users.getPasswordHash(), users.isActive(), users.getId()};
    }

    @Override
    public Users map(ResultSet resultSet) throws DaoException {
        Users users = new Users();
        try {
            users.setId(resultSet.getInt(ID));
            users.setUsername(resultSet.getString(USER_NAME));
            users.setPasswordHash(resultSet.getString(USER_PASS));
            var aBoolean = resultSet.getBoolean(USER_ACTIVITY);
            users.setActive(aBoolean);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return users;
    }

    public int updateCase(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_ACTIVITY), users.isActive(), users.getId());
    }

    public int updateAvailable(Users users) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, USER_AVAILABLE), users.getUser_available(), users.getId());
    }
}
