package com.hamza.account.features.audit;

import java.util.Locale;
import java.util.Map;

/** Stable translation keys for administration event types, with unknown legacy values left readable. */
public final class AuditAdminEventLabels {

    private static final Map<String, String> KEYS = Map.of(
            "EXPORT", "audit.admin.event.export",
            "ADMIN_EXPORT", "audit.admin.event.admin.export",
            "DELETE_SELECTED", "audit.admin.event.delete.selected",
            "RETENTION_POLICY", "audit.admin.event.retention.policy",
            "RETENTION_CLEANUP", "audit.admin.event.retention.cleanup");

    private AuditAdminEventLabels() {
    }

    public static String keyFor(String eventType) {
        return eventType == null ? null : KEYS.get(eventType.trim().toUpperCase(Locale.ROOT));
    }
}
