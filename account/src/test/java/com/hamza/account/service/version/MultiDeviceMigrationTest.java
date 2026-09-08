package com.hamza.account.service.version;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.SharedSettingKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four migrations that make a second computer safe, held to what the Java expects of
 * them.
 * <p>
 * No database: what can be checked by reading is that each migration exists, that it
 * creates the table its class names, and - the part worth a test rather than a glance -
 * that the migration and the code have not drifted apart on the two strings they share:
 * a permission key, and a settings key.
 */
class MultiDeviceMigrationTest {

    private static String read(String migration) throws IOException {
        try (InputStream stream = MultiDeviceMigrationTest.class
                .getResourceAsStream("/db/migration/" + migration)) {
            assertNotNull(stream, migration + " is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("V38 gives every machine its own trial row, and clears the shared lockout")
    void trialIsPerMachine() throws IOException {
        String sql = read("V38__trial_per_machine.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS trial_machine_state"));
        assertTrue(sql.contains("machine_id"), "the row is keyed by the machine, or it is the old shared row again");
        assertTrue(sql.contains("trial_fail_count"));
        // The counter that locked the first machine out has to be released by the move.
        // Carrying it across would migrate the defect along with the data.
        assertTrue(sql.contains("SET trial_fail_count = 0"));
        // The company columns are evidence of an install's history and are not dropped.
        assertFalse(sql.contains("DROP COLUMN"));
    }

    @Test
    @DisplayName("V39 declares both backup keys and grants them to whoever could already reach the screen")
    void backupPermissionsExistAndAreGranted() throws IOException {
        String sql = read("V39__backup_permissions.sql");
        assertTrue(sql.contains("'setting.backup.show'"));
        assertTrue(sql.contains("'backup.restore'"));
        assertTrue(sql.contains("auth_role_permission"), "a declared key nobody holds takes the screen away from everyone");
        assertTrue(sql.contains("'setting.show'"), "the grant is to the roles that could open the settings yesterday");

        // The migration and the catalogue name the same keys, or the startup
        // synchronisation and the grant are talking about different rows.
        assertNotNull(AppPermissions.fromValue("setting.backup.show"));
        assertNotNull(AppPermissions.fromValue("backup.restore"));
    }

    @Test
    @DisplayName("V40 creates the shared settings table the shop's policies live in")
    void sharedSettingsTableExists() throws IOException {
        String sql = read("V40__app_settings.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS app_setting"));
        assertTrue(sql.contains("setting_key"));
        assertTrue(sql.contains("setting_value"));
        // Deleting a user must not be refused by a settings row, and must not take one
        // with it either - the value is the shop's, the name on it is only a stamp.
        assertTrue(sql.contains("ON DELETE SET NULL"));
    }

    @Test
    @DisplayName("V41 creates the machine registry the connected-machines screen reads")
    void workstationRegistryExists() throws IOException {
        String sql = read("V41__workstation_sessions.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS workstation_session"));
        assertTrue(sql.contains("app_version"));
        assertTrue(sql.contains("database_version"), "an out-of-date machine is the thing this screen exists to show");
        assertTrue(sql.contains("last_seen"));
    }

    @Test
    @DisplayName("the backup owner is a shared setting, so one machine answers for the shop")
    void backupOwnerIsShared() {
        assertTrue(SharedSettingKeys.isShared(SharedSettingKeys.BACKUP_OWNER_MACHINE));
    }

    @Test
    @DisplayName("V50 gives item and document edits a sub-second version for optimistic locking")
    void editVersionsHaveFractionalPrecision() throws IOException {
        String sql = read("V50__optimistic_lock_versions.sql");
        for (String table : new String[]{
                "items", "total_sales", "total_buy", "total_sales_re", "total_buy_re"}) {
            assertTrue(sql.contains("ALTER TABLE " + table), table + " is missing its edit version migration");
        }
        assertTrue(sql.contains("TIMESTAMP(6)"));
        assertTrue(sql.contains("ON UPDATE CURRENT_TIMESTAMP(6)"));
    }

    @Test
    @DisplayName("V51 gives every change topic a monotonic revision")
    void dataChangesUseARevisionRatherThanClockOrdering() throws IOException {
        String sql = read("V51__data_change_revisions.sql");
        assertTrue(sql.contains("ADD COLUMN revision BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("TIMESTAMP(6)"));
    }

    @Test
    @DisplayName("V52 gives customer and supplier edits precise versions")
    void partyEditVersionsHaveFractionalPrecision() throws IOException {
        String sql = read("V52__party_optimistic_lock_versions.sql");
        assertTrue(sql.contains("ALTER TABLE custom"));
        assertTrue(sql.contains("ALTER TABLE suppliers"));
        assertTrue(sql.contains("TIMESTAMP(6)"));
    }
}
