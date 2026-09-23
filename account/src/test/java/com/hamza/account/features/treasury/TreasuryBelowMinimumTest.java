package com.hamza.account.features.treasury;

import com.hamza.account.treasury.TreasuryStatements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The minimum-balance warning: its rule in Java, the same rule in the statement, and what V69 adds. */
class TreasuryBelowMinimumTest {

    @Test
    @DisplayName("a minimum of zero is none set, and a balance exactly at the minimum is enough")
    void theBoundaries() {
        assertFalse(TreasuryBelowMinimum.isBelow(new BigDecimal("-50"), BigDecimal.ZERO), "no minimum, no warning");
        assertFalse(TreasuryBelowMinimum.isBelow(new BigDecimal("500"), new BigDecimal("500")));
        assertTrue(TreasuryBelowMinimum.isBelow(new BigDecimal("499.99"), new BigDecimal("500")));
        assertTrue(TreasuryBelowMinimum.isBelow(null, new BigDecimal("1")), "a treasury with nothing in it is below");
        assertFalse(TreasuryBelowMinimum.isBelow(BigDecimal.ONE, null));
    }

    @Test
    @DisplayName("what has to be moved in is the gap, and never negative")
    void shortBy() {
        assertEquals(0, new BigDecimal("120.50").compareTo(
                new TreasuryBelowMinimum(2, "wallet", new BigDecimal("379.50"), new BigDecimal("500")).shortBy()));
        assertEquals(0, new TreasuryBelowMinimum(2, "wallet", new BigDecimal("900"), new BigDecimal("500"))
                .shortBy().signum());
    }

    @Test
    @DisplayName("the statement states the same rule: active, a minimum set, strictly under it - in its own currency")
    void theStatementSaysTheSame() {
        assertEquals("""
                SELECT b.id, b.t_name, b.balance_own AS balance, t.min_balance
                FROM treasury_current_balance b
                         JOIN treasury t ON t.id = b.id
                WHERE b.is_active = 1
                  AND t.min_balance > 0
                  AND b.balance_own < t.min_balance
                ORDER BY b.sort_order, b.id
                """, TreasuryStatements.SELECT_BELOW_MINIMUM);
    }

    @Test
    @DisplayName("V69 defaults the minimum to zero, so an upgrade raises no warning for anybody")
    void theMigrationDefaultsToNoMinimum() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V69__treasury_account_and_minimum.sql")) {
            assertTrue(in != null, "V69 is not on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("'min_balance'"));
        assertTrue(sql.contains("DECIMAL(14,2) NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("'account_number'"));
        assertTrue(sql.contains("VARCHAR(60) NULL"), "a drawer has no number, and blank is stored as NULL");
    }
}
