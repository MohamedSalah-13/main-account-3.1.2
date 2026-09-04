package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftCashHandoverServiceTest {
    private FakeRepository repository;
    private ShiftCashHandoverService service;

    @BeforeEach
    void setUp() {
        UserSessionContext session = new UserSessionContext();
        session.signIn(7, "supervisor", List.of(
                AppPermissions.SHIFT_POLICY_MANAGE, AppPermissions.SHIFT_FORCE_CLOSE));
        ServiceRegistry.register(UserSessionContext.class, session);
        repository = new FakeRepository();
        service = new ShiftCashHandoverService(repository, null, session, Clock.systemUTC());
    }

    @Test
    void closeRequestUsesTheCountedAmountAndCashierIdentity() throws Exception {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 4, 7, 30);

        assertTrue(service.requestForClosedShift(12, 3, new BigDecimal("125.55555"), 9, requestedAt));
        assertEquals(12, repository.shiftId);
        assertEquals(3, repository.sourceTreasuryId);
        assertEquals(new BigDecimal("125.56"), repository.actualBalance);
        assertEquals(9, repository.handedByUserId);
        assertEquals(requestedAt, repository.requestedAt);
    }

    @Test
    void policyRejectsMissingFloatAndSameTreasuryBeforePersistence() {
        assertThrows(UserValidationException.class,
                () -> service.savePolicy(3, 4, null, true));
        assertThrows(UserValidationException.class,
                () -> service.savePolicy(3, 3, BigDecimal.ZERO, true));
    }

    @Test
    void pendingHandoverBlocksOpening() {
        repository.blocking = true;
        assertThrows(BusinessRuleException.class, () -> service.requireTreasuryReadyForOpen(3));
    }

    @Test
    void supervisorOverrideRequiresAReason() {
        assertThrows(UserValidationException.class,
                () -> service.approveOpenOverride(44, "  "));
    }

    /**
     * The service holds a null {@code DaoFactory} here, so a settlement that reached the
     * treasury at all would fail loudly. Passing proves the drawer was already square and
     * nothing was posted - the case every non-RECONCILE treasury closes in.
     */
    @Test
    void aDrawerThatMatchesTheCountPostsNothing() throws Exception {
        service.settleCloseVariance(12, 3, new BigDecimal("400.001"), new BigDecimal("400.004"),
                7, LocalDateTime.of(2026, 9, 4, 7, 30));
        assertEquals(0, repository.varianceAdjustments);
    }

    /**
     * Settling and declaring are separate calls with separate arguments; a handover carries
     * only what was counted. This pins that the declaration takes no expected balance, which
     * is what let the settlement stop depending on a handover policy existing.
     */
    @Test
    void settlementRejectsMissingBalancesBeforeReachingTheTreasury() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 4, 7, 30);
        assertThrows(IllegalArgumentException.class,
                () -> service.settleCloseVariance(12, 3, null, BigDecimal.TEN, 7, at));
        assertThrows(IllegalArgumentException.class,
                () -> service.settleCloseVariance(12, 3, BigDecimal.TEN, null, 7, at));
        assertThrows(IllegalArgumentException.class,
                () -> service.requestForClosedShift(12, 3, null, 9, at));
    }

    private static final class FakeRepository implements ShiftCashHandoverRepository {
        private int shiftId;
        private int sourceTreasuryId;
        private BigDecimal actualBalance;
        private int handedByUserId;
        private LocalDateTime requestedAt;
        private boolean blocking;
        private int varianceAdjustments;

        @Override public List<ShiftCashHandoverPolicy> loadPolicies() { return List.of(); }
        @Override public void savePolicy(int source, int target, BigDecimal retained,
                                         boolean enabled, int actor) { }
        @Override public int appendForClosedShift(int shift, int source, BigDecimal actual,
                                                  int cashier, LocalDateTime at) {
            shiftId = shift;
            sourceTreasuryId = source;
            actualBalance = actual;
            handedByUserId = cashier;
            requestedAt = at;
            return 1;
        }
        @Override public List<ShiftCashHandover> loadPending() { return List.of(); }
        @Override public ShiftCashHandover findForUpdate(long handoverId) { return null; }
        @Override public int insertReceipt(long handoverId, int receiver, LocalDateTime at,
                                           int transferId, String note) { return 0; }
        @Override public int appendVarianceAdjustment(int shiftId, int treasuryId,
                                                       BigDecimal expected, BigDecimal actual,
                                                       BigDecimal difference, int movementId,
                                                       int actor, LocalDateTime at) {
            varianceAdjustments++;
            return 1;
        }
        @Override public boolean hasBlockingPendingHandover(int treasuryId) { return blocking; }
        @Override public int insertOpenOverride(long handoverId, int actor, String reason,
                                                LocalDateTime at) {
            return 1;
        }
    }
}
