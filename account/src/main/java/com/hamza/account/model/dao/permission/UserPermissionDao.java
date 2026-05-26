package com.hamza.account.model.dao.permission;

import com.hamza.account.model.domain.permission.UserPermission;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Set;

public interface UserPermissionDao {

    List<UserPermission> findByUserId(int userId) throws DaoException;

    Set<String> findGrantedPermissionCodesByUserId(int userId) throws DaoException;

    boolean hasDirectPermission(int userId, String permissionCode) throws DaoException;

    int upsertUserPermission(int userId, int permissionId, boolean checked) throws DaoException;

    int upsertUserPermissionByCode(int userId, String permissionCode, boolean checked) throws DaoException;

    int updateUserPermissions(int userId, List<UserPermission> permissions) throws DaoException;

    int insertMissingPermissionsForUser(int userId) throws DaoException;

    int insertPermissionForAllUsers(int permissionId) throws DaoException;
}