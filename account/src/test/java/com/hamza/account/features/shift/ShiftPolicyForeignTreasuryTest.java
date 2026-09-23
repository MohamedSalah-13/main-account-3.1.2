package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.treasury.TreasuryCurrencyGuard;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A treasury in a foreign currency runs no shift (V81, docs/currency-plan.md §11 ق-ب٣): the policy
 * service refuses to track one, before any transaction opens - and the application gives it the guard
 * that knows, where the short constructors the older tests use leave it out.
 */
class ShiftPolicyForeignTreasuryTest {

    private final List<TreasuryShiftPolicy> saved = new ArrayList<>();
    private final ShiftPolicyService service = new ShiftPolicyService(new Policies(), null, null,
            new TreasuryCurrencyGuard(id -> id == 9 ? "درج الدولار" : null));

    @BeforeEach
    void signIn() {
        UserSessionContext session = new UserSessionContext();
        session.signIn(7, "manager", List.of(AppPermissions.SHIFT_POLICY_MANAGE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    @Test
    @DisplayName("tracking a treasury in a foreign currency is refused, in either road, and nothing is saved")
    void refused() {
        for (ShiftTrackingMode mode : List.of(ShiftTrackingMode.TRACK_ONLY, ShiftTrackingMode.RECONCILE)) {
            assertThrows(BusinessRuleException.class,
                    () -> service.saveTreasury(new TreasuryShiftPolicy(9, "درج الدولار", mode)));
            ShiftPolicy policy = new ShiftPolicy(ShiftMode.OPTIONAL, false, false, BigDecimal.ZERO,
                    false, false, false);
            assertThrows(BusinessRuleException.class, () -> service.saveConfiguration(policy,
                    List.of(new TreasuryShiftPolicy(1, "الخزينة", ShiftTrackingMode.RECONCILE),
                            new TreasuryShiftPolicy(9, "درج الدولار", mode))));
        }
        assertTrue(saved.isEmpty());
    }

    private final class Policies implements ShiftPolicyRepository {
        @Override public ShiftPolicy load() {
            return new ShiftPolicy(ShiftMode.OPTIONAL, false, false, BigDecimal.ZERO, false, false, false);
        }
        @Override public List<TreasuryShiftPolicy> loadTreasuries() { return List.of(); }
        @Override public ShiftTrackingMode trackingMode(int treasuryId) { return ShiftTrackingMode.NONE; }
        @Override public boolean hasOpenShifts() { return false; }
        @Override public void save(ShiftPolicy policy) { }
        @Override public void saveTreasury(TreasuryShiftPolicy policy) { saved.add(policy); }
    }
}
