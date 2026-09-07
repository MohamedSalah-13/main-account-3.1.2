package com.hamza.account.features.notification;

import com.hamza.controlsfx.notifications.NotificationSeverity;

import java.util.List;

/** Localized-message recipe kept free of JavaFX so notification rules are unit-testable. */
public record AuditNotificationSpec(String key,
                                    NotificationSeverity severity,
                                    String titleKey,
                                    String messageKey,
                                    List<Object> arguments) {
    public AuditNotificationSpec {
        arguments = List.copyOf(arguments);
    }
}
