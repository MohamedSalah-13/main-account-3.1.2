package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;

/** Authorization boundary for the immutable administration journal. */
public record AuditAdminEventService(AuditAdminEventRepository repository) {

    public AuditAdminEventPage load(AuditAdminEventQuery query) throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_ADMIN_VIEW);
        return repository.load(query);
    }

    public AuditAdminOptions options() throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_ADMIN_VIEW);
        return repository.options();
    }
}
