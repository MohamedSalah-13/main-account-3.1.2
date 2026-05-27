package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.RolePermissionDao;
import com.hamza.account.model.domain.permission.RolePermission;
import com.hamza.account.service.permission.RolePermissionService;
import com.hamza.controlsfx.database.DaoException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RolePermissionDao rolePermissionDao;

    @Override
    public List<RolePermission> getRolePermissions(int roleId) throws DaoException {
        rolePermissionDao.insertMissingPermissionsForRole(roleId);
        return rolePermissionDao.findByRoleId(roleId);
    }

    @Override
    public int saveRolePermissions(int roleId, List<RolePermission> permissions) throws DaoException {
        return rolePermissionDao.updateRolePermissions(roleId, permissions);
    }

    @Override
    public int syncMissingPermissionsForRole(int roleId) throws DaoException {
        return rolePermissionDao.insertMissingPermissionsForRole(roleId);
    }
}
