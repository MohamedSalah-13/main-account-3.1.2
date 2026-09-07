package com.hamza.account.features.users;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;

/** Queries intentionally shaped for the management screen, without credentials. */
public record UsersManagementService(DaoFactory daoFactory) {

    public UserManagementPage load(UserManagementQuery query) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USERS_SHOW);
        return daoFactory.usersDao().managementPage(query);
    }
}
