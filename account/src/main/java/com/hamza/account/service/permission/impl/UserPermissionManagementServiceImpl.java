package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.UserPermissionDao;
import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.account.security.cache.PermissionCacheManager;
import com.hamza.account.service.permission.UserPermissionManagementService;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UserPermissionManagementServiceImpl implements UserPermissionManagementService {

    private final UserPermissionDao userPermissionDao;

    @Override
    public List<UserPermission> getUserPermissions(int userId) throws DaoException {
        userPermissionDao.insertMissingPermissionsForUser(userId);
        return userPermissionDao.findByUserId(userId);
    }

    @Override
    public int saveUserPermissions(int userId, List<UserPermission> permissions) throws DaoException {
        int result = userPermissionDao.updateUserPermissions(userId, permissions);

        // مسح Cache للمستخدم فقط
        PermissionCacheManager.invalidateUserCache(userId);

        return result;
    }

    @Override
    public int syncMissingPermissionsForUser(int userId) throws DaoException {
        return userPermissionDao.insertMissingPermissionsForUser(userId);
    }
}
