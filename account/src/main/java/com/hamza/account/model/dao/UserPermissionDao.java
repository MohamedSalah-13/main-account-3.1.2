package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Users;
import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class UserPermissionDao extends AbstractDao<Users_Permission> {

    private final String TABLE_NAME = "user_permission";
    private final String ID = "id";
    private final String PERMISSION_ID = "permission_id";
    private final String USER_ID = "user_id";
    private final String CHECK_STATUS = "check_status";

    UserPermissionDao(Connection connection) {
        super(connection);
    }


    @Override
    public List<Users_Permission> loadAllById(int id) throws DaoException {
//        String sql ="select * from user_permission\n" +
//                "join permission p on p.id = user_permission.permission_id where user_permission.user_id=?";
        return queryForObjects(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, USER_ID), this::map, id);
    }

    @Override
    public int update(Users_Permission usersPermission) throws DaoException {
        String query = """
                UPDATE user_permission
                set check_status = ?
                where user_id = ?
                  and permission_id = ?""";
        return executeUpdate(query, getData(usersPermission));
    }

    @Override
    public Object[] getData(Users_Permission usersPermission) throws DaoException {
        return new Object[]{usersPermission.isStatus(), usersPermission.getUser_id(), usersPermission.getUserPermissionType().getId()};
    }

    @Override
    public Users_Permission map(ResultSet rs) throws DaoException {
        Users_Permission usersPermission = new Users_Permission();
        try {
            usersPermission.setId(rs.getInt(ID));
            usersPermission.setUserPermissionType(UserPermissionType.getUserPermissionById(rs.getInt(PERMISSION_ID)));
            usersPermission.setUsers(new Users(rs.getInt(USER_ID)));
            var aBoolean = rs.getBoolean(CHECK_STATUS);
            usersPermission.setStatus(aBoolean);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return usersPermission;
    }

    @Override
    public int updateList(List<Users_Permission> list) throws DaoException {
        try {
            String query = """
                    UPDATE user_permission
                    set check_status = ?
                    where user_id = ?
                      and permission_id = ?""";
            return executeUpdateListWithException(list, query
                    , (statement, usersPermission) ->
                            setData(statement, getData(usersPermission)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }


}
