package com.hamza.account.features.notification;

import com.hamza.account.features.audit.AuditOperationEvent;
import com.hamza.controlsfx.notifications.NotificationSeverity;

import java.util.List;
import java.util.Optional;

/** Decides whether and how a completed audit operation should be announced. */
public final class AuditNotificationPlan {

    private AuditNotificationPlan() {
    }

    public static Optional<AuditNotificationSpec> forEvent(AuditOperationEvent event,
                                                            boolean mayViewAdministration) {
        if (event instanceof AuditOperationEvent.RetentionCleanupCompleted cleanup
                && cleanup.automatic() && (cleanup.rows() == 0 || !mayViewAdministration)) {
            return Optional.empty();
        }
        return Optional.of(switch (event) {
            case AuditOperationEvent.Exported exported -> new AuditNotificationSpec(
                    exported.administration() ? "audit.operation.admin-export" : "audit.operation.export",
                    NotificationSeverity.SUCCESS,
                    exported.administration()
                            ? "audit.notification.admin.export.title" : "audit.notification.export.title",
                    exported.administration()
                            ? "audit.notification.admin.export.message" : "audit.notification.export.message",
                    List.of(exported.rows(), exported.target().getFileName()));
            case AuditOperationEvent.Deleted deleted -> new AuditNotificationSpec(
                    "audit.operation.delete", NotificationSeverity.WARNING,
                    "audit.notification.delete.title", "audit.notification.delete.message",
                    List.of(deleted.rows()));
            case AuditOperationEvent.RetentionPolicyChanged policy -> new AuditNotificationSpec(
                    "audit.operation.retention-policy", NotificationSeverity.WARNING,
                    "audit.notification.retention.policy.title",
                    policy.enabled()
                            ? "audit.notification.retention.policy.enabled"
                            : "audit.notification.retention.policy.disabled",
                    policy.enabled() ? List.of(policy.days()) : List.of());
            case AuditOperationEvent.RetentionCleanupCompleted cleanup -> new AuditNotificationSpec(
                    cleanup.automatic() ? "audit.operation.retention-auto" : "audit.operation.retention-manual",
                    NotificationSeverity.WARNING, "audit.notification.retention.cleanup.title",
                    cleanup.automatic()
                            ? "audit.notification.retention.cleanup.automatic"
                            : "audit.notification.retention.cleanup.manual",
                    List.of(cleanup.rows()));
        });
    }
}
