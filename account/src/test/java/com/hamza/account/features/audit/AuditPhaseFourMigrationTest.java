package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPhaseFourMigrationTest {

    @Test
    void migrationAddsTheAdminExportPermissionWithAConservativeRoleGrant() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V49__audit_activity_and_admin_export.sql"));

        assertTrue(migration.contains("'audit.admin.export'"));
        assertTrue(migration.contains("admin_view_permission.permission_key = 'audit.admin.view'"));
        assertTrue(migration.contains("ordinary_export_permission.permission_key = 'audit.export'"));
    }

    @Test
    void migrationIndexesTheBoundedDirectDatabaseActivityQuery() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V49__audit_activity_and_admin_export.sql"));

        assertTrue(migration.contains("idx_audit_source_time"));
        assertTrue(migration.contains("audit_log (source, action_time)"));
    }
}
