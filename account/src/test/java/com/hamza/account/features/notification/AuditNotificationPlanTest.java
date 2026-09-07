package com.hamza.account.features.notification;

import com.hamza.account.features.audit.AuditExportFormat;
import com.hamza.account.features.audit.AuditOperationEvent;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditNotificationPlanTest {

    @Test
    void administrationExportProducesASuccessNotificationWithFileAndRowCount() {
        AuditNotificationSpec spec = AuditNotificationPlan.forEvent(
                new AuditOperationEvent.Exported(AuditExportFormat.EXCEL,
                        Path.of("administration.xlsx"), 12, true), true).orElseThrow();

        assertEquals(NotificationSeverity.SUCCESS, spec.severity());
        assertEquals("audit.notification.admin.export.message", spec.messageKey());
        assertEquals(12, spec.arguments().get(0));
        assertEquals(Path.of("administration.xlsx"), spec.arguments().get(1));
    }

    @Test
    void automaticCleanupWithoutDeletedRowsStaysSilent() {
        assertTrue(AuditNotificationPlan.forEvent(
                new AuditOperationEvent.RetentionCleanupCompleted(0, true), true).isEmpty());
    }

    @Test
    void automaticCleanupIsVisibleOnlyToAuditAdministrators() {
        assertTrue(AuditNotificationPlan.forEvent(
                new AuditOperationEvent.RetentionCleanupCompleted(4, true), false).isEmpty());
        assertEquals(NotificationSeverity.WARNING, AuditNotificationPlan.forEvent(
                new AuditOperationEvent.RetentionCleanupCompleted(4, true), true)
                .orElseThrow().severity());
    }
}
