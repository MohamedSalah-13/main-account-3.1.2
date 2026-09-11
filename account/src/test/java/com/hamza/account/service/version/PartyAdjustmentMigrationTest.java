package com.hamza.account.service.version;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads {@code V55__party_account_adjustment.sql} and checks it does what the code assumes.
 * <p>
 * Two assumptions, and both would fail silently. The permission keys the Java side requires have
 * to be the ones the migration creates, or {@code AuthorizationGuard.require} refuses a movement
 * nobody can ever be granted. And the grant has to reach the roles that already hold
 * {@code account.create}, or everybody loses the ability to record a movement on upgrade — which
 * is how {@code V34} was written, and why.
 * <p>
 * It reads the file rather than a database, in the manner of {@code BootstrapPasswordMigrationTest}
 * and {@code MultiDeviceMigrationTest}, so it runs on every build. What it cannot say is that the
 * SQL executes: {@code PartyMovementDatabaseAcceptanceTest} is gated and covers that.
 */
class PartyAdjustmentMigrationTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V55__party_account_adjustment.sql");

    private static String sql;

    @BeforeAll
    static void read() {
        try {
            sql = Files.readString(MIGRATION);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + MIGRATION, e);
        }
    }

    @Test
    @DisplayName("the keys the code requires are the keys the migration creates")
    void bothAdjustPermissionsAreCreated() {
        for (PermissionKey key : new PermissionKey[]{
                AppPermissions.CUSTOMER_ACCOUNT_ADJUST, AppPermissions.SUPPLIERS_ACCOUNT_ADJUST}) {
            assertTrue(sql.contains("'" + key.value() + "'"),
                    key.value() + " is required by AppPermissions but not created by V55. "
                            + "A key with no row is a permission nobody can be granted.");
        }
    }

    /**
     * Nobody loses an ability on upgrade.
     * <p>
     * The grant selects the roles that already hold {@code account.create} and gives them
     * {@code account.adjust} - the {@code V34} pattern. An administrator may then take it away
     * from whoever should not have it, which is a decision somebody makes rather than a default
     * that silently removes a screen's button on the morning after an update.
     */
    @Test
    @DisplayName("the grant follows whoever could already record a movement")
    void theGrantFollowsTheCreatePermission() {
        for (String side : new String[]{"customer", "suppliers"}) {
            assertTrue(sql.contains("'" + side + ".account.create'"),
                    side + ".account.adjust must be granted to the roles holding "
                            + side + ".account.create, as V34 granted items.group.move");
        }
        assertTrue(sql.contains("INSERT IGNORE INTO auth_role_permission"),
                "the grant must tolerate being applied where it already is");
    }

    /**
     * The two columns carry a comment saying what they are.
     * <p>
     * The same reason {@code V20} put one on {@code treasury.amount}: a column called
     * {@code purchase} on a table called {@code customers_accounts} does not tell a reader that it
     * is the debit side of a movement, and the next person to read the schema directly is the one
     * who has to guess.
     */
    @Test
    void theColumnsAreDocumentedInTheSchema() {
        assertTrue(sql.contains("MODIFY COLUMN purchase"), "purchase must be given a COMMENT");
        assertTrue(sql.contains("MODIFY COLUMN numberInv"), "numberInv must be given a COMMENT");
        assertTrue(sql.contains("customers_accounts") && sql.contains("suppliers_accounts"),
                "both ledgers, not one: they are the same table twice");
    }

    /**
     * The composite index the statement needs, created by a procedure this file defines itself.
     * <p>
     * Every party statement filters on (party, date) together, and the foreign key covers the
     * first column alone. MySQL has no {@code CREATE INDEX IF NOT EXISTS}, so one is written.
     * <p>
     * <b>The first draft called {@code add_index_if_missing}, and that would have failed on every
     * install.</b> {@code V1} creates that procedure at its line 311, uses it some eighty times,
     * and <b>drops it at its line 994</b> - so nothing after the baseline can call it, and an
     * existing install stamped at {@code V1} never created it at all. It is the
     * {@code V1_1__audit_log_procedure.sql} scar in a new place: a versioned migration may not
     * depend on anything a versioned migration before it does not leave behind. {@code V21},
     * {@code V22} and {@code V23} each define a local copy for exactly this reason, and that is
     * what this one does.
     * <p>
     * No test in a green build could see it: the SQL is only wrong when MySQL reads it. It was
     * found by migrating a scratch schema from empty, which is the one thing that proves a
     * migration applies at all.
     */
    @Test
    void theStatementGetsItsCompositeIndexFromASelfContainedProcedure() {
        assertTrue(sql.contains("CREATE PROCEDURE add_party_index_if_missing"),
                "V55 must define the helper it calls: V1 drops add_index_if_missing at its own end");
        assertFalse(sql.contains("CALL add_index_if_missing("),
                "calling V1's add_index_if_missing fails on every install - it is dropped by V1 "
                        + "line 994 and never exists on a stamped install. Define a local copy, "
                        + "as V21, V22 and V23 do.");
        assertTrue(sql.contains("CALL add_party_index_if_missing('customers_accounts'"), sql);
        assertTrue(sql.contains("CALL add_party_index_if_missing('suppliers_accounts'"), sql);
        assertTrue(sql.contains("account_code, account_date"),
                "the index must cover both columns the statement filters on");
        assertTrue(sql.trim().endsWith("DROP PROCEDURE IF EXISTS add_party_index_if_missing;"),
                "the helper is dropped again, so it cannot be mistaken for a lasting one");
    }
}
