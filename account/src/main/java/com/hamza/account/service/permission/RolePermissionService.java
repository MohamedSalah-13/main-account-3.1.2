package com.hamza.account.service.permission;

import com.hamza.account.model.domain.permission.RolePermission;
import com.hamza.account.database.DaoException;

import java.util.List;

public interface RolePermissionService {

    List<RolePermission> getRolePermissions(int roleId) throws DaoException;

    int saveRolePermissions(int roleId, List<RolePermission> permissions) throws DaoException;

    int syncMissingPermissionsForRole(int roleId) throws DaoException;
}
