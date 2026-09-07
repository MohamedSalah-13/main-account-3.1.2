package com.hamza.account.features.notification;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.audit.AuditOperationEvent;
import com.hamza.account.features.audit.AuditOperationListener;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.NotificationSeverity;

/** Converts completed sensitive audit operations into localized in-app notifications. */
public final class AuditOperationNotifier implements AuditOperationListener {

    @Override
    public void onCompleted(AuditOperationEvent event) {
        LanguageManager language = LanguageManager.getInstance();
        AuditNotificationPlan.forEvent(event,
                        AuthorizationGuard.isGranted(AppPermissions.AUDIT_ADMIN_VIEW))
                .ifPresent(spec -> publish(spec, language));
    }

    private static void publish(AuditNotificationSpec spec, LanguageManager language) {
        String title = language.getString(spec.titleKey());
        String message = language.getString(spec.messageKey(), spec.arguments().toArray());
        if (spec.severity() == NotificationSeverity.SUCCESS) {
            AppNotifications.success(spec.key(), NotificationCategories.AUDIT, title, message);
        } else {
            AppNotifications.warn(spec.key(), NotificationCategories.AUDIT, title, message);
        }
    }
}
