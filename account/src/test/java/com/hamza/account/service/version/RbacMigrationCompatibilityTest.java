package com.hamza.account.service.version;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacMigrationCompatibilityTest {

    @Test
    void usesMySqlCompatibleAlterTableSyntax() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V11__rbac.sql")) {
            assertTrue(stream != null, "V11 migration is missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertFalse(sql.contains("ADD COLUMN IF NOT EXISTS"));
            assertTrue(sql.contains("ADD COLUMN CATEGORY"));
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS ROLES"));
        }
    }

    @Test
    void modernAuthorizationMigrationRemovesTheLegacyMatrix() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V12__modern_authorization.sql")) {
            assertTrue(stream != null, "V12 migration is missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertTrue(sql.contains("DROP TABLE USER_PERMISSION"));
            assertTrue(sql.contains("CREATE TABLE AUTH_ROLE_INHERITANCE"));
            assertTrue(sql.contains("CREATE TABLE AUTH_USER_PERMISSION_OVERRIDE"));
            assertFalse(sql.contains("ADD COLUMN IF NOT EXISTS"));
        }
    }

    @Test
    void starterRolesAreLeastPrivilegeAndNeverAutoAssigned() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V13__default_rbac_roles.sql")) {
            assertTrue(stream != null, "V13 migration is missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertTrue(sql.contains("DEFAULT_SALES_CASHIER"));
            assertTrue(sql.contains("DEFAULT_SALES_MANAGER"));
            assertTrue(sql.contains("DEFAULT_PURCHASES"));
            assertTrue(sql.contains("DEFAULT_INVENTORY"));
            assertTrue(sql.contains("DEFAULT_ACCOUNTANT"));
            assertTrue(sql.contains("DEFAULT_SECURITY_ADMIN"));
            assertTrue(sql.contains("AUTH_ROLE_INHERITANCE"));
            assertFalse(sql.contains("AUTH_USER_ROLE"), "migration must not guess user assignments");

            var matcher = Pattern.compile("'([a-z][a-z0-9.]+)'").matcher(sql.toLowerCase());
            while (matcher.find()) {
                String permission = matcher.group(1);
                assertNotNull(AppPermissions.fromValue(permission), "unknown starter-role permission " + permission);
            }
        }
    }

    @Test
    void cashierShiftGrantWorksForUpgradeAndFreshInstall() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V29__grant_cashier_self_shift_permissions.sql")) {
            assertTrue(stream != null, "V29 migration is missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertTrue(sql.contains("INSERT INTO AUTH_PERMISSION"),
                    "fresh installs need the permissions before catalogue synchronization");
            assertTrue(sql.contains("DEFAULT_SALES_CASHIER"));
            assertTrue(sql.contains("SHIFT.SELF.VIEW"));
            assertTrue(sql.contains("SHIFT.SELF.OPEN"));
            assertTrue(sql.contains("SHIFT.SELF.CLOSE"));
            assertTrue(sql.contains("SHIFT.XREPORT.VIEW"));
            assertFalse(sql.contains("AUTH_USER_ROLE"),
                    "the migration must grant a role, not guess which users are cashiers");
        }
    }

    @Test
    void cashierTreasuryAssignmentMigrationStartsInSafeRolloutMode() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V30__cashier_treasury_assignments.sql")) {
            assertTrue(stream != null, "V30 migration is missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();

            assertTrue(sql.contains("ENFORCE_TREASURY_ASSIGNMENTS BOOLEAN NOT NULL DEFAULT FALSE"));
            assertTrue(sql.contains("CREATE TABLE CASHIER_TREASURY_ASSIGNMENT"));
            assertTrue(sql.contains("UNIQUE (USER_ID, TREASURY_ID)"));
            assertTrue(sql.contains("UNIQUE (DEFAULT_USER_ID)"));
            assertTrue(sql.contains("INFORMATION_SCHEMA.COLUMNS"),
                    "the policy column must be retryable after non-transactional MySQL DDL");
            assertTrue(sql.contains("FOREIGN KEY (USER_ID) REFERENCES USERS(ID) ON DELETE RESTRICT"));
            assertTrue(sql.contains("FOREIGN KEY (TREASURY_ID) REFERENCES TREASURY(ID) ON DELETE CASCADE"));
        }
    }
}
