package com.hamza.account.service.permission;

import com.hamza.account.type.PermissionCode;
import com.hamza.account.database.DaoException;

public interface AuthorizationService {

    boolean hasPermission(int userId, String permissionCode) throws DaoException;

    boolean hasPermission(int userId, PermissionCode permissionCode) throws DaoException;

    boolean hasAnyPermission(int userId, PermissionCode... permissionCodes) throws DaoException;

    boolean hasAllPermissions(int userId, PermissionCode... permissionCodes) throws DaoException;

    void requirePermission(int userId, PermissionCode permissionCode) throws DaoException;
}
