package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.authorization.PermissionRisk;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the service decides before it reaches a database, and what V73 has to say for the rule
 * to mean anything. <b>The session is never user 1</b>, who bypasses every permission.
 * <p>
 * The write itself runs inside {@code TransactionTemplate} and is not covered here;
 * {@code DiscountCeilingDatabaseAcceptanceTest} is its proof.
 */
class DiscountCeilingServiceTest {

    private final Ceilings ceilings = new Ceilings();
    private final DiscountCeilingService service = new DiscountCeilingService(ceilings);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    void readingACeilingNeedsTheCommissionPermission() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.ceilingOf(5));
        assertEquals(0, ceilings.reads, "refused before any query");
    }

    @Test
    void decidingACeilingNeedsMoreThanSeeingOne() {
        signInWith(AppPermissions.COMMISSION_SHOW, AppPermissions.SALES_DISCOUNT_OVERRIDE);
        assertThrows(BusinessRuleException.class, () -> service.update(5, new BigDecimal("10")));
        assertEquals(0, ceilings.writes);
    }

    @Test
    void aCeilingOutsideZeroToAHundredIsRefusedWithTheKeyOfTheSentence() {
        signInWith(AppPermissions.COMMISSION_RULE_UPDATE);
        for (String bad : new String[]{"-1", "100.01", "250"}) {
            UserValidationException refusal = assertThrows(UserValidationException.class,
                    () -> service.update(5, new BigDecimal(bad)));
            assertEquals("delegate.ceiling.error.range", refusal.getMessage());
        }
        assertEquals(0, ceilings.writes);
    }

    /** Granted to every cashier, a ceiling the owner sets would stop nobody. */
    @Test
    void theMigrationGrantsTheOverrideToWhoeverSetsTheCeilingNotToWhoeverSells() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V73__delegate_discount_ceiling.sql"));
        String grant = sql.substring(sql.indexOf("INSERT IGNORE INTO auth_role_permission"));
        assertTrue(grant.contains("held.permission_key = 'commission.rule.update'"));
        assertFalse(grant.contains("'sales.create'"));
    }

    /** NULL is no ceiling: adding the column must not give anybody one. */
    @Test
    void theMigrationGivesNobodyACeiling() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V73__delegate_discount_ceiling.sql"));
        assertTrue(sql.contains("DECIMAL(5,2) NULL"));
        assertFalse(sql.contains("UPDATE employees"), "no backfill: a guessed ceiling refuses real invoices");
        assertFalse(sql.contains("DEFAULT 0"));
    }

    /** The risk is derived from the key's last word, and the default for an unknown word is LOW. */
    @Test
    void theOverrideIsNotALowRiskPermission() throws Exception {
        assertEquals(PermissionRisk.HIGH, AppPermissions.definitions().stream()
                .filter(definition -> definition.key().equals(AppPermissions.SALES_DISCOUNT_OVERRIDE))
                .findFirst().orElseThrow().risk());
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V73__delegate_discount_ceiling.sql"));
        assertTrue(sql.contains("'OVERRIDE', 'HIGH'"));
    }

    private static final class Ceilings implements DiscountCeilingRepository {
        private int reads;
        private int writes;

        @Override
        public Optional<DiscountCeiling> ceilingOf(int employeeId) {
            reads++;
            return Optional.empty();
        }

        @Override
        public int write(int employeeId, BigDecimal maxPercent) {
            writes++;
            return 1;
        }
    }
}
