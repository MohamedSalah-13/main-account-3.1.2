package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPhaseThreeMigrationTest {

    @Test
    void migrationAddsAdministrationReadPermissionAndQueryIndexes() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V48__audit_administration_browser.sql"));

        assertTrue(migration.contains("'audit.admin.view'"));
        assertTrue(migration.contains("idx_audit_admin_source_time"));
        assertTrue(migration.contains("idx_audit_admin_actor_time"));
    }

    @Test
    void repeatableTriggersCoverCriticalAuthorizationAssignments() throws Exception {
        String triggers = Files.readString(Path.of("src/main/resources/db/migration/R__triggers.sql"));

        assertTrue(triggers.contains("CREATE TRIGGER audit_auth_role_insert"));
        assertTrue(triggers.contains("CREATE TRIGGER audit_auth_role_permission_insert"));
        assertTrue(triggers.contains("CREATE TRIGGER audit_auth_user_role_insert"));
        assertTrue(triggers.contains("CREATE TRIGGER audit_auth_role_inheritance_insert"));
        assertTrue(triggers.contains("CREATE TRIGGER audit_auth_override_update"));
    }
}
