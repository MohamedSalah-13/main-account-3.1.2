package com.hamza.account.service.permission;

import com.hamza.account.model.domain.permission.Permission;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.database.DaoException;

import java.util.List;

public interface PermissionService {

    List<Permission> getAllPermissions() throws DaoException;

    List<Permission> getActivePermissions() throws DaoException;

    void syncPermissionsFromCode() throws DaoException;

    boolean exists(PermissionCode permissionCode) throws DaoException;
}
