package com.hamza.account.service.version;

import com.hamza.account.security.PasswordHasher;
import com.hamza.account.wipe.WipeCatalog;
import com.hamza.account.wipe.WipePlan;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapPasswordMigrationTest {

    @Test
    void wipeDoesNotRestoreTheAdministratorAsPlainText() {
        String statements = String.join("\n", WipePlan.of(java.util.List.of(WipeCatalog.USERS)).statements());

        assertFalse(statements.contains("user_pass = 'admin'"));
        assertTrue(statements.contains("must_change_password = 1"));
    }

    @Test
    void forwardOnlyMigrationHashesTheHistoricalCredentialAndRequiresAChange() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V44__force_bootstrap_password_change.sql")) {
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(migration.contains("must_change_password"));
            assertTrue(migration.contains("$2a$12$"));
            assertTrue(migration.contains("WHERE id = 1"));
        }
    }

    /**
     * The shape of the hash was all this file used to check. What matters is which
     * password it is a hash of: V1 seeds {@code admin/admin} and V44 replaces that value
     * in place, so a hash of anything else locks every existing installation out of the
     * only account that can sign in - and it would still contain "$2a$12$".
     */
    @Test
    void theReplacementHashIsOfTheCredentialV1ActuallySeeded() throws Exception {
        assertTrue(PasswordHasher.matches(seededBootstrapPassword(), replacementHash()).matched(),
                "V44 replaced the V1 bootstrap credential with a hash of a different password");
    }

    private static String replacementHash() throws Exception {
        return firstGroup("/db/migration/V44__force_bootstrap_password_change.sql",
                "user_pass = '(\\$2[aby]\\$\\d{2}\\$[^']+)'");
    }

    private static String seededBootstrapPassword() throws Exception {
        return firstGroup("/db/migration/V1__baseline.sql",
                "VALUES \\(1, 'admin', '([^']+)'");
    }

    private static String firstGroup(String resource, String regex) throws Exception {
        try (var input = BootstrapPasswordMigrationTest.class.getResourceAsStream(resource)) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile(regex).matcher(sql);
            assertTrue(matcher.find(), "no match for " + regex + " in " + resource);
            return matcher.group(1);
        }
    }
}
