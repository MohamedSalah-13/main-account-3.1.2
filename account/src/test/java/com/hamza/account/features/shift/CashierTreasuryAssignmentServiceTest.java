package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashierTreasuryAssignmentServiceTest {
    private UserSessionContext session;
    private FakeAssignments assignments;

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        session.signIn(7, "cashier", java.util.Set.of(
                AppPermissions.SHIFT_SELF_VIEW, AppPermissions.SHIFT_POLICY_MANAGE));
        ServiceRegistry.register(UserSessionContext.class, session);
        assignments = new FakeAssignments();
    }

    @Test
    void strictPolicyReturnsOnlyRepositoryAssignedTreasuriesForCurrentCashier() throws Exception {
        CashierTreasuryAssignmentService service = service(true);

        assertEquals(List.of(new CashierTreasuryChoice(3, "Till 3", true)),
                service.availableTreasuries(7));
        assertEquals(7, assignments.requestedUser);
        assertTrue(assignments.requestedEnforcement);
    }

    @Test
    void myShiftCannotReadAnotherUsersTreasuries() {
        CashierTreasuryAssignmentService service = service(true);

        assertThrows(BusinessRuleException.class, () -> service.availableTreasuries(8));
    }

    @Test
    void accessIsOnlyRestrictedAfterTheSafeRolloutFlagIsEnabled() throws Exception {
        assignments.allowed = false;

        assertTrue(service(false).canOpenShift(7, 3));
        assertFalse(service(true).canOpenShift(7, 3));
    }

    @Test
    void strictPolicyCannotBeEnabledBeforeAtLeastOneAssignmentExists() {
        assignments.hasActiveAssignments = false;
        ShiftPolicyService policyService = new ShiftPolicyService(
                new FakePolicies(true), null, assignments);
        ShiftPolicy strict = new ShiftPolicy(ShiftMode.REQUIRED, false, true,
                BigDecimal.ZERO, true, false, true);

        assertThrows(BusinessRuleException.class, () -> policyService.saveConfiguration(
                strict, List.of(new TreasuryShiftPolicy(3, "Till 3", ShiftTrackingMode.RECONCILE))));
    }

    private CashierTreasuryAssignmentService service(boolean enforce) {
        ShiftPolicyRepository policyRepository = new FakePolicies(enforce);
        return new CashierTreasuryAssignmentService(assignments,
                new ShiftPolicyService(policyRepository), session);
    }

    private static final class FakeAssignments implements CashierTreasuryAssignmentRepository {
        private int requestedUser;
        private boolean requestedEnforcement;
        private boolean allowed = true;
        private boolean hasActiveAssignments = true;

        @Override public List<CashierTreasuryAssignment> loadAll() { return List.of(); }
        @Override public List<CashierTreasuryChoice> availableTreasuries(
                int userId, boolean enforceAssignments) {
            requestedUser = userId;
            requestedEnforcement = enforceAssignments;
            return List.of(new CashierTreasuryChoice(3, "Till 3", true));
        }
        @Override public CashierTreasuryAssignment findById(int assignmentId, boolean forUpdate) {
            return null;
        }
        @Override public boolean canOpenShift(int userId, int treasuryId) { return allowed; }
        @Override public boolean isAssignable(int userId, int treasuryId) { return true; }
        @Override public boolean hasOpenShift(int userId, int treasuryId) { return false; }
        @Override public boolean hasActiveAssignments() { return hasActiveAssignments; }
        @Override public void lockUser(int userId) { }
        @Override public void clearDefault(int userId, int actorUserId) { }
        @Override public void upsert(int userId, int treasuryId, boolean defaultTreasury, int actorUserId) { }
        @Override public int deactivate(int assignmentId, int actorUserId) { return 1; }
    }

    private record FakePolicies(boolean enforce) implements ShiftPolicyRepository {
        @Override public ShiftPolicy load() {
            return new ShiftPolicy(ShiftMode.REQUIRED, false, true, BigDecimal.ZERO,
                    true, false, enforce);
        }
        @Override public List<TreasuryShiftPolicy> loadTreasuries() { return List.of(); }
        @Override public ShiftTrackingMode trackingMode(int treasuryId) {
            return ShiftTrackingMode.RECONCILE;
        }
        @Override public boolean hasOpenShifts() { return false; }
        @Override public void save(ShiftPolicy policy) { }
        @Override public void saveTreasury(TreasuryShiftPolicy policy) { }
    }
}
