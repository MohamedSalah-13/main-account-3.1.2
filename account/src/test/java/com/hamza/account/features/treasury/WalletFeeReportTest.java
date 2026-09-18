package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fee report's decisions, over rows in memory - that the SQL groups them right is the acceptance test's to say. */
class WalletFeeReportTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    private static final List<WalletFeeReport.Row> ROWS = List.of(
            new WalletFeeReport.Row(2, "wallet", ShiftCashSource.SALES, 3, new BigDecimal("30.50")),
            new WalletFeeReport.Row(2, "wallet", ShiftCashSource.TRANSFER_OUT, 1, new BigDecimal("5")),
            new WalletFeeReport.Row(2, "wallet", null, 2, new BigDecimal("4.50")));

    @Test
    @DisplayName("the totals carry the unlinked fees too, or they would not be the heading's total")
    void totalsIncludeUnlinkedFees() {
        assertEquals(0, new BigDecimal("40.00").compareTo(WalletFeeReport.totalFees(ROWS)));
        assertEquals(6, WalletFeeReport.totalMovements(ROWS));
        assertEquals(0, WalletFeeReport.totalFees(List.of()).signum());
    }

    @Test
    @DisplayName("a kind is named as the treasury statement names it; no kind has words of its own")
    void kindsAreNamedOnce() {
        assertEquals("treasury.statement.movement.sales", ROWS.get(0).kindLabelKey());
        assertEquals("treasury.statement.movement.transfer.out", ROWS.get(1).kindLabelKey());
        assertEquals("treasury.fee.report.kind.unlinked", ROWS.get(2).kindLabelKey());
    }

    @Test
    @DisplayName("a stored code resolves to its kind, NULL to none, and an unknown one is refused")
    void storedCodes() {
        assertEquals(ShiftCashSource.CUSTOMER_ACCOUNT, WalletFeeReport.kindOf(5));
        assertNull(WalletFeeReport.kindOf(null));
        assertThrows(IllegalArgumentException.class, () -> WalletFeeReport.kindOf(99));
    }

    @Test
    @DisplayName("permission first, then the period; the rows come back as they were read")
    void guardThenPeriod() throws Exception {
        WalletFeeReport report = new WalletFeeReport((from, to, treasury) -> ROWS);

        signIn();
        assertThrows(BusinessRuleException.class, () -> report.between(DAY, DAY, null));

        signIn(AppPermissions.TREASURY_SHOW);
        assertThrows(IllegalArgumentException.class, () -> report.between(DAY, DAY.minusDays(1), null));
        assertEquals(ROWS, report.between(DAY, DAY, null));
    }

    @Test
    @DisplayName("the statement finds the fee heading by its key, never by a name somebody can change")
    void theStatementReadsTheHeadingByKey() {
        String sql = TreasuryStatements.SELECT_WALLET_FEE_REPORT;

        assertTrue(sql.contains("e.system_key = '" + ExpenseHeading.WALLET_FEE + "'"));
        assertTrue(sql.contains("AND (? IS NULL OR d.treasury_id = ?)"));
        assertTrue(sql.contains("GROUP BY d.treasury_id, t.t_name, d.fee_source_type"));
        assertEquals(4, sql.chars().filter(c -> c == '?').count());
    }

    private static void signIn(com.hamza.account.authorization.PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }
}
