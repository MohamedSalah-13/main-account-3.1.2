package com.hamza.account.service.permission;

import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.account.database.DaoException;

import java.util.List;

public interface UserPermissionManagementService {

    List<UserPermission> getUserPermissions(int userId) throws DaoException;

    int saveUserPermissions(int userId, List<UserPermission> permissions) throws DaoException;

    int syncMissingPermissionsForUser(int userId) throws DaoException;
}
