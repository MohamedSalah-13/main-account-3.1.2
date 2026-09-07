package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.util.List;

/** Authorization boundary for browsing and deleting audit records. */
public final class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditOperationListener listener;

    public AuditLogService(AuditLogRepository repository) {
        this(repository, AuditOperationListener.NONE);
    }

    public AuditLogService(AuditLogRepository repository, AuditOperationListener listener) {
        this.repository = repository;
        this.listener = listener == null ? AuditOperationListener.NONE : listener;
    }

    public AuditLogPage load(AuditLogQuery query) throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_VIEW);
        return repository.load(query);
    }

    public AuditLogOptions options() throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_VIEW);
        return repository.options();
    }

    public int delete(List<Long> ids, String reason) throws DaoException, UserValidationException {
        AuthorizationGuard.require(AppPermissions.AUDIT_DELETE);
        String safeReason = requireReason(reason);
        int deleted = TransactionTemplate.execute(() -> repository.delete(ids, safeReason));
        listener.notifySafely(new AuditOperationEvent.Deleted(deleted));
        return deleted;
    }

    static String requireReason(String reason) throws UserValidationException {
        String safe = reason == null ? "" : reason.trim();
        if (safe.length() < 5 || safe.length() > 500) {
            throw new UserValidationException("audit.log.reason.validation");
        }
        return safe;
    }
}
