package com.hamza.account.model.dao.permission;

import com.hamza.account.database.DaoException;

import java.util.Set;

public interface AuthorizationDao {

    boolean hasPermission(int userId, String permissionCode) throws DaoException;

    Set<String> findEffectivePermissionCodes(int userId) throws DaoException;
}
