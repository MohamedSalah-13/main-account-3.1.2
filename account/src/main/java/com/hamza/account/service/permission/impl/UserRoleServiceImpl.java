package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.UserRoleDao;
import com.hamza.account.model.domain.permission.UserRole;
import com.hamza.account.service.permission.UserRoleService;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleDao userRoleDao;

    @Override
    public List<UserRole> getUserRoles(int userId) throws DaoException {
        return userRoleDao.findByUserId(userId);
    }

    @Override
    public int assignRoleToUser(int userId, int roleId) throws DaoException {
        return userRoleDao.assignRoleToUser(userId, roleId);
    }

    @Override
    public int removeRoleFromUser(int userId, int roleId) throws DaoException {
        return userRoleDao.removeRoleFromUser(userId, roleId);
    }

    @Override
    public int replaceUserRoles(int userId, List<Integer> roleIds) throws DaoException {
        return userRoleDao.replaceUserRoles(userId, roleIds);
    }
}
