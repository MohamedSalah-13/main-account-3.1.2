package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPhaseTwoMigrationTest {

    @Test
    void migrationAddsAppendOnlyJournalSafeDefaultsAndDedicatedPermissions() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V47__audit_administration.sql"));
        String procedures = Files.readString(Path.of(
                "src/main/resources/db/migration/R__procedures.sql"));
        String triggers = Files.readString(Path.of(
                "src/main/resources/db/migration/R__triggers.sql"));

        assertTrue(migration.contains("CREATE TABLE audit_admin_event"));
        assertTrue(migration.contains("'audit.retention.enabled', 'false'"));
        assertTrue(migration.contains("'audit.export'"));
        assertTrue(migration.contains("'audit.retention.manage'"));
        assertTrue(procedures.contains("CREATE PROCEDURE write_audit_admin_event"));
        assertTrue(triggers.contains("CREATE TRIGGER prevent_audit_admin_event_update"));
        assertTrue(triggers.contains("CREATE TRIGGER prevent_audit_admin_event_delete"));
        String immutableJournalTriggers = triggers.substring(
                triggers.indexOf("CREATE TRIGGER prevent_audit_admin_event_update"),
                triggers.indexOf("DELIMITER ;") + "DELIMITER ;".length());
        assertFalse(immutableJournalTriggers.contains("@app_bulk_wipe"));
    }
}
