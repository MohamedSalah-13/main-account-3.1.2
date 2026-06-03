package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.UserRole;
import com.hamza.account.database.DaoException;

import java.util.List;
import java.util.Set;

public interface UserRoleDao {

    List<UserRole> findByUserId(int userId) throws DaoException;

    Set<Integer> findRoleIdsByUserId(int userId) throws DaoException;

    int assignRoleToUser(int userId, int roleId) throws DaoException;

    int removeRoleFromUser(int userId, int roleId) throws DaoException;

    int removeAllRolesFromUser(int userId) throws DaoException;

    int replaceUserRoles(int userId, List<Integer> roleIds) throws DaoException;
}