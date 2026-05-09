package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UsersDao extends AbstractDao<Users> {

    public static final String USER_NAME = "user_name";
    private static final int FILTER_LIMIT = 50;
    private static final String FILTER_USERS_SQL_NUMERIC = """
            SELECT * FROM users
            WHERE id = ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_USERS_SQL_TEXT_STARTS = """
            SELECT * FROM users
            WHERE user_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_USERS_SQL_TEXT_CONTAINS = """
            SELECT * FROM users
            WHERE user_name LIKE ?
            ORDER BY id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
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
        if (id <= 0)
            throw new IllegalArgumentException("Invalid user ID: " + id);
        if (id == 1)
            throw new IllegalArgumentException("Cannot delete user with ID 1");
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

    public Optional<Users> getUserByNameAndPassword(String username, String password) throws DaoException {
        String query = "SELECT * FROM users WHERE user_name = ? AND user_pass = ?";
        var users = queryForObject(query, this::map, username, password);
        return Optional.ofNullable(users);
    }

    public List<Users> getFilterUsers(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects("SELECT * FROM users ORDER BY id DESC LIMIT " + FILTER_LIMIT, this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            }

            return queryForObjects(FILTER_USERS_SQL_NUMERIC, this::map, id);
        }

        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Users> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        List<Users> starts = queryForObjects(FILTER_USERS_SQL_TEXT_STARTS, this::map, likeStarts);
        for (Users u : starts) {
            if (u != null) result.putIfAbsent(u.getId(), u);
        }

        if (result.size() < FILTER_LIMIT) {
            List<Users> contains = queryForObjects(FILTER_USERS_SQL_TEXT_CONTAINS, this::map, likeContains);
            for (Users u : contains) {
                if (u != null) result.putIfAbsent(u.getId(), u);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<Users> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects("SELECT * FROM users ORDER BY id DESC LIMIT ? OFFSET ?", this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForInt("SELECT COUNT(*) FROM users");
    }
}
