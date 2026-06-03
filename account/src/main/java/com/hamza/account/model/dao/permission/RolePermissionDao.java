package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.RolePermission;
import com.hamza.account.database.DaoException;

import java.util.List;
import java.util.Set;

public interface RolePermissionDao {

    List<RolePermission> findByRoleId(int roleId) throws DaoException;

    Set<String> findGrantedPermissionCodesByRoleId(int roleId) throws DaoException;

    Set<String> findGrantedPermissionCodesByUserId(int userId) throws DaoException;

    int upsertRolePermission(int roleId, int permissionId, boolean checked) throws DaoException;

    int upsertRolePermissionByCode(int roleId, String permissionCode, boolean checked) throws DaoException;

    int updateRolePermissions(int roleId, List<RolePermission> permissions) throws DaoException;

    int insertMissingPermissionsForRole(int roleId) throws DaoException;
}