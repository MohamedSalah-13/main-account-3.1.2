package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** Safe retention workflow: preview first, disabled by default, and every mutation journalled. */
public final class AuditRetentionService {

    private static final Duration AUTOMATIC_INTERVAL = Duration.ofHours(24);

    private final AuditLogRepository repository;
    private final Clock clock;
    private final AuditOperationListener listener;

    public AuditRetentionService(AuditLogRepository repository) {
        this(repository, AuditOperationListener.NONE);
    }

    public AuditRetentionService(AuditLogRepository repository, AuditOperationListener listener) {
        this(repository, Clock.systemDefaultZone(), listener);
    }

    AuditRetentionService(AuditLogRepository repository, Clock clock) {
        this(repository, clock, AuditOperationListener.NONE);
    }

    AuditRetentionService(AuditLogRepository repository, Clock clock, AuditOperationListener listener) {
        this.repository = repository;
        this.clock = clock;
        this.listener = listener == null ? AuditOperationListener.NONE : listener;
    }

    public AuditRetentionPolicy policy() throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_RETENTION_MANAGE);
        return repository.retentionPolicy();
    }

    public AuditRetentionPreview preview(int days) throws DaoException {
        AuthorizationGuard.require(AppPermissions.AUDIT_RETENTION_MANAGE);
        LocalDateTime cutoff = cutoff(days);
        return new AuditRetentionPreview(cutoff, repository.countBefore(cutoff));
    }

    public void save(boolean enabled, int days, String reason) throws DaoException, UserValidationException {
        AuthorizationGuard.require(AppPermissions.AUDIT_RETENTION_MANAGE);
        String safeReason = AuditLogService.requireReason(reason);
        AuditRetentionPolicy current = repository.retentionPolicy();
        AuditRetentionPolicy updated = new AuditRetentionPolicy(enabled, days, current.lastRunAt());
        TransactionTemplate.execute(() -> {
            repository.saveRetentionPolicy(updated, safeReason);
            return null;
        });
        listener.notifySafely(new AuditOperationEvent.RetentionPolicyChanged(enabled, days));
    }

    public int cleanNow(int days, String reason) throws DaoException, UserValidationException {
        AuthorizationGuard.require(AppPermissions.AUDIT_RETENTION_MANAGE);
        String safeReason = AuditLogService.requireReason(reason);
        LocalDateTime cutoff = cutoff(days);
        int deleted = TransactionTemplate.execute(() -> repository.purgeBefore(cutoff, safeReason, false));
        listener.notifySafely(new AuditOperationEvent.RetentionCleanupCompleted(deleted, false));
        return deleted;
    }

    /** Scheduler entry point. It deliberately has no user-permission dependency and records SYSTEM as actor. */
    int runAutomaticIfDue() throws DaoException {
        AuditRetentionPolicy policy = repository.retentionPolicy();
        if (!policy.enabled() || !due(policy.lastRunAt())) return 0;
        LocalDateTime cutoff = cutoff(policy.days());
        int deleted = TransactionTemplate.execute(() -> repository.purgeBefore(
                cutoff, "AUTOMATIC_RETENTION_POLICY", true));
        listener.notifySafely(new AuditOperationEvent.RetentionCleanupCompleted(deleted, true));
        return deleted;
    }

    private LocalDateTime cutoff(int days) {
        new AuditRetentionPolicy(false, days, null);
        return LocalDateTime.now(clock).minusDays(days);
    }

    private boolean due(LocalDateTime lastRun) {
        return lastRun == null || lastRun.plus(AUTOMATIC_INTERVAL).isBefore(LocalDateTime.now(clock));
    }
}
