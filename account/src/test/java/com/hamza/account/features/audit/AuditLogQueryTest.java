package com.hamza.account.features.audit;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionRisk;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditLogQueryTest {

    @Test
    void recentCoversThirtyInclusiveDays() {
        AuditLogQuery query = AuditLogQuery.recent(LocalDate.of(2026, 9, 7));

        assertEquals(LocalDate.of(2026, 8, 9), query.from());
        assertEquals(LocalDate.of(2026, 9, 7), query.to());
        assertEquals(0, query.page());
        assertEquals(50, query.pageSize());
    }

    @Test
    void normalizesOptionalValuesAndPaging() {
        AuditLogQuery query = new AuditLogQuery("  invoice  ", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2), 0, null, " total_sales ", null, null, -2, 500);

        assertEquals("invoice", query.search());
        assertEquals("TOTAL_SALES", query.tableName());
        assertEquals(AuditActionFilter.ALL, query.action());
        assertEquals(AuditSourceFilter.ALL, query.source());
        assertEquals(AuditLogSort.NEWEST, query.sort());
        assertEquals(0, query.page());
        assertEquals(200, query.pageSize());
    }

    @Test
    void rejectsAnInvertedDateRangeWithAUiMessageKey() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AuditLogQuery("", LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1),
                        null, null, null, null, null, 0, 50));

        assertEquals("audit.log.validation.date.range", error.getMessage());
    }

    @Test
    void auditViewIsCataloguedAsAHighRiskRead() {
        PermissionRisk risk = AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.AUDIT_VIEW))
                .findFirst().orElseThrow().risk();

        assertEquals(PermissionRisk.HIGH, risk);
    }

    @Test
    void auditAdministrationPermissionsCarryTheirDataRisk() {
        PermissionRisk export = AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.AUDIT_EXPORT))
                .findFirst().orElseThrow().risk();
        PermissionRisk retention = AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.AUDIT_RETENTION_MANAGE))
                .findFirst().orElseThrow().risk();
        PermissionRisk adminView = AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.AUDIT_ADMIN_VIEW))
                .findFirst().orElseThrow().risk();
        PermissionRisk adminExport = AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.AUDIT_ADMIN_EXPORT))
                .findFirst().orElseThrow().risk();

        assertEquals(PermissionRisk.HIGH, export);
        assertEquals(PermissionRisk.CRITICAL, retention);
        assertEquals(PermissionRisk.HIGH, adminView);
        assertEquals(PermissionRisk.HIGH, adminExport);
    }
}
