package com.hamza.account.service.permission.impl;


import com.hamza.account.model.dao.permission.RolePermissionDao;
import com.hamza.account.model.dao.permission.UserPermissionDao;
import com.hamza.account.service.permission.AuthorizationService;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserPermissionDao userPermissionDao;
    private final RolePermissionDao rolePermissionDao;

    @Override
    public boolean hasPermission(int userId, String permissionCode) throws DaoException {
        Set<String> permissions = new HashSet<>();
        permissions.addAll(userPermissionDao.findGrantedPermissionCodesByUserId(userId));
        permissions.addAll(rolePermissionDao.findGrantedPermissionCodesByUserId(userId));

        return permissions.contains(permissionCode);
    }

    @Override
    public boolean hasPermission(int userId, PermissionCode permissionCode) throws DaoException {
        return hasPermission(userId, permissionCode.getCode());
    }

    @Override
    public boolean hasAnyPermission(int userId, PermissionCode... permissionCodes) throws DaoException {
        for (PermissionCode permissionCode : permissionCodes) {
            if (hasPermission(userId, permissionCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllPermissions(int userId, PermissionCode... permissionCodes) throws DaoException {
        for (PermissionCode permissionCode : permissionCodes) {
            if (!hasPermission(userId, permissionCode)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void requirePermission(int userId, PermissionCode permissionCode) throws DaoException {
        if (!hasPermission(userId, permissionCode)) {
            throw new DaoException("ليس لديك صلاحية: " + permissionCode.getTitleAr());
        }
    }
}
