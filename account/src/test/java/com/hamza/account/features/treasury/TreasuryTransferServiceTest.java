package com.hamza.account.features.treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusals a transfer has to make, in the order it has to make them.
 * <p>
 * No database and no JavaFX. The two rules that need MySQL - the period lock and the
 * balance - are checked by {@code TreasuryBalanceViewAcceptanceTest}; what is checked
 * here is that the cheap refusals happen <b>before</b> the transaction opens, which
 * is both the correct order and the one a reader would get wrong: a user who may not
 * transfer must not learn the balances from an error message, and a transfer to
 * itself must not take a row lock first.
 */
class TreasuryTransferServiceTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /**
     * User 2, never user 1: {@code UserSessionContext.isSystemAdministrator()} is
     * {@code currentUserId() == 1}, and an administrator holds every permission
     * whatever the set says - so signing in as user 1 would make the permission case
     * pass for the wrong reason and hide a missing guard.
     */
    private void signInWith(com.hamza.account.authorization.PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "cashier", java.util.List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private TreasuryTransferCommand command(int from, int to, String amount) {
        return new TreasuryTransferCommand(from, to,
                amount == null ? null : new BigDecimal(amount),
                LocalDate.now(), "test", 1);
    }

    /**
     * The service is never constructed here: every case must be refused before any DAO
     * is reached, so a null factory is the assertion. A rule that moved below the
     * database access would fail with a NullPointerException rather than pass quietly.
     */
    private TreasuryTransferService serviceWithoutDatabase() {
        return new TreasuryTransferService(null);
    }

    @Test
    @DisplayName("a user without treasury.transfer is refused before anything is read")
    void permissionComesFirst() {
        signInWith(AppPermissions.TREASURY_SHOW);

        Exception refusal = assertThrows(Exception.class,
                () -> serviceWithoutDatabase().transfer(command(1, 2, "10")));

        assertTrue(refusal instanceof BusinessRuleException,
                "a permission denial is a business rule, not a technical failure: " + refusal);
    }

    @Test
    @DisplayName("a treasury cannot transfer to itself")
    void sameTreasuryIsRefused() {
        signInWith(AppPermissions.TREASURY_TRANSFER);

        BusinessRuleException refusal = assertThrows(BusinessRuleException.class,
                () -> serviceWithoutDatabase().transfer(command(3, 3, "10")));

        assertTrue(refusal.getMessage() != null && !refusal.getMessage().isBlank());
    }

    @Test
    @DisplayName("zero, negative and missing amounts are refused")
    void theAmountMustBePositive() {
        signInWith(AppPermissions.TREASURY_TRANSFER);

        for (String amount : new String[]{"0", "-5", null}) {
            assertThrows(BusinessRuleException.class,
                    () -> serviceWithoutDatabase().transfer(command(1, 2, amount)),
                    "accepted an amount of " + amount);
        }
    }

    @Test
    @DisplayName("the balance check refuses one penny too much and allows exactly the balance")
    void requireEnoughIsInclusiveAtTheBalance() {
        TreasuryBalanceSummary treasury = summary(HUNDRED);

        assertDoesNotThrow(() -> TreasuryTransferService.requireEnough(treasury, HUNDRED),
                "emptying a treasury completely is allowed");
        assertDoesNotThrow(() -> TreasuryTransferService.requireEnough(treasury, new BigDecimal("99.99")));
        assertThrows(BusinessRuleException.class,
                () -> TreasuryTransferService.requireEnough(treasury, new BigDecimal("100.01")));
    }

    @Test
    @DisplayName("a treasury already negative refuses any withdrawal")
    void aNegativeTreasuryCannotPay() {
        TreasuryBalanceSummary treasury = summary(new BigDecimal("-1.00"));

        assertThrows(BusinessRuleException.class,
                () -> TreasuryTransferService.requireEnough(treasury, new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("the refusal names the treasury and what it holds")
    void theRefusalIsUseful() {
        BusinessRuleException refusal = assertThrows(BusinessRuleException.class,
                () -> TreasuryTransferService.requireEnough(summary(HUNDRED), new BigDecimal("500")));

        assertTrue(refusal.getMessage().contains("فودافون كاش"),
                "the refusal does not say which treasury: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("100.00"),
                "the refusal does not say what is available: " + refusal.getMessage());
    }

    @Test
    @DisplayName("the direction of a cash movement is the stored code, both ways")
    void cashDirectionRoundTrips() {
        assertEquals(1, CashDirection.DEPOSIT.code());
        assertEquals(2, CashDirection.WITHDRAWAL.code());
        assertEquals(CashDirection.DEPOSIT, CashDirection.fromCode(1));
        assertEquals(CashDirection.WITHDRAWAL, CashDirection.fromCode(2));
        assertThrows(IllegalArgumentException.class, () -> CashDirection.fromCode(3));

        assertTrue(CashDirection.WITHDRAWAL.leavesTheTreasury());
        assertTrue(!CashDirection.DEPOSIT.leavesTheTreasury(),
                "a deposit must not be checked against the balance");
    }

    private TreasuryBalanceSummary summary(BigDecimal balance) {
        return new TreasuryBalanceSummary(2, "فودافون كاش", TreasuryType.WALLET, true, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, balance);
    }
}
