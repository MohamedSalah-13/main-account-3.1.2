package com.hamza.account.authorization;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

/** Authorization boundary for legacy generic DAO write seams. */
public final class AuthorizedDataWriter {

    private AuthorizedDataWriter() {
    }

    public static <T> int save(DaoList<T> dao, T value, boolean updating,
                               PermissionKey createPermission, PermissionKey updatePermission)
            throws DaoException {
        AuthorizationGuard.require(updating ? updatePermission : createPermission);
        return updating ? dao.update(value) : dao.insert(value);
    }

    public static <T> int insert(DaoList<T> dao, T value, PermissionKey permission) throws DaoException {
        AuthorizationGuard.require(permission);
        return dao.insert(value);
    }
}
