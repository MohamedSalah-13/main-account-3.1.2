package com.hamza.account.service.permission;

import com.hamza.account.model.domain.permission.UserRole;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface UserRoleService {

    List<UserRole> getUserRoles(int userId) throws DaoException;

    int assignRoleToUser(int userId, int roleId) throws DaoException;

    int removeRoleFromUser(int userId, int roleId) throws DaoException;

    int replaceUserRoles(int userId, List<Integer> roleIds) throws DaoException;
}
