package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogMigrationTest {

    @Test
    void migrationPreservesActorAndWorkstationAndIndexesThePrimaryRange() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V46__audit_log_integrity_and_access.sql"));

        assertTrue(sql.contains("ADD COLUMN actor_name"));
        assertTrue(sql.contains("ADD COLUMN workstation_id"));
        assertTrue(sql.contains("CREATE INDEX idx_audit_time ON audit_log (action_time)"));
        assertTrue(sql.contains("'audit.view'"));
        assertTrue(sql.contains("'setting.show', 'audit.delete'"));
    }

    @Test
    void procedureUsesBorrowedConnectionContextAndDoesNotFallBackToAdmin() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/R__procedures.sql"));
        String procedure = sql.substring(sql.indexOf("CREATE PROCEDURE write_audit_log"),
                sql.indexOf("DROP PROCEDURE IF EXISTS max_item_id"));

        assertTrue(procedure.contains("@app_audit_source"));
        assertTrue(procedure.contains("@app_user_id"));
        assertTrue(procedure.contains("@app_machine_id"));
        assertTrue(procedure.contains("CURRENT_USER()"));
        assertTrue(procedure.contains("SET v_user_id = NULL"));
    }
}
