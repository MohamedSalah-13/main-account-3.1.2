package com.hamza.account.features.delegate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statements character for character, and each one's parameter count. A merge that swaps
 * two adjacent columns still produces valid SQL - it just stores a threshold as a rate.
 */
class CommissionRuleQueryTest {

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    @Test
    void theInsertIsPinned() {
        assertEquals("""
                INSERT INTO employee_commission_rule (employee_id, effective_from, basis, tier_mode, target,
                       tier1_from, tier1_rate, tier2_from, tier2_rate, tier3_from, tier3_rate, notes, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", CommissionRuleQuery.INSERT_SQL);
        assertEquals(13, parameters(CommissionRuleQuery.INSERT_SQL));
    }

    /** Who entered a rule is not restamped by an edit; that is the audit log's question. */
    @Test
    void theUpdateIsPinnedAndLeavesTheAuthorAlone() {
        assertEquals("""
                UPDATE employee_commission_rule
                SET basis = ?, tier_mode = ?, target = ?,
                    tier1_from = ?, tier1_rate = ?, tier2_from = ?, tier2_rate = ?,
                    tier3_from = ?, tier3_rate = ?, notes = ?
                WHERE employee_id = ?
                  AND effective_from = ?""", CommissionRuleQuery.UPDATE_SQL);
        assertEquals(12, parameters(CommissionRuleQuery.UPDATE_SQL));
        assertFalse(CommissionRuleQuery.UPDATE_SQL.contains("user_id"));
    }

    /** Both keys: an id from one delegate's screen cannot remove another delegate's rule. */
    @Test
    void theDeleteNamesTheRuleAndItsDelegate() {
        assertEquals("""
                DELETE FROM employee_commission_rule
                WHERE id = ?
                  AND employee_id = ?""", CommissionRuleQuery.DELETE_SQL);
        assertEquals(2, parameters(CommissionRuleQuery.DELETE_SQL));
    }

    /** A rule dated in the future is in the table and is not the answer until its day. */
    @Test
    void theRuleInForceIsTheLatestThatHadStarted() {
        assertEquals("""
                SELECT id, employee_id, effective_from, basis, tier_mode, target,
                       tier1_from, tier1_rate, tier2_from, tier2_rate, tier3_from, tier3_rate, notes
                FROM employee_commission_rule
                WHERE employee_id = ?
                  AND effective_from <= ?
                ORDER BY effective_from DESC
                LIMIT 1""", CommissionRuleQuery.IN_FORCE_SQL);
        assertEquals(2, parameters(CommissionRuleQuery.IN_FORCE_SQL));
    }

    @Test
    void theHistoryIsNewestFirst() {
        assertTrue(CommissionRuleQuery.HISTORY_SQL.endsWith("ORDER BY effective_from DESC"));
        assertEquals(1, parameters(CommissionRuleQuery.HISTORY_SQL));
        assertEquals(1, parameters(CommissionRuleQuery.IS_DELEGATE_SQL));
        assertTrue(CommissionRuleQuery.IS_DELEGATE_SQL.contains("j.is_delegate = 1"));
    }

    /** An unused tier is two NULLs - V70's CHECKs refuse a tier with one half. */
    @Test
    void unusedTiersAreBoundAsNulls() {
        CommissionTiers two = new CommissionTiers(List.of(
                new CommissionTiers.Tier(new BigDecimal("50"), new BigDecimal("1")),
                new CommissionTiers.Tier(new BigDecimal("100"), new BigDecimal("3"))));
        assertArrayEquals(new Object[]{new BigDecimal("50"), new BigDecimal("1"),
                        new BigDecimal("100"), new BigDecimal("3"), null, null},
                JdbcCommissionRuleRepository.tierColumns(two));
    }
}
