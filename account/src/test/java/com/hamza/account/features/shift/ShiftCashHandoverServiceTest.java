package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.UserValidationException;
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
        session.signIn(7, "supervisor", List.of(AppPermissions.SHIFT_POLICY_MANAGE));
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

    private static final class FakeRepository implements ShiftCashHandoverRepository {
        private int shiftId;
        private int sourceTreasuryId;
        private BigDecimal actualBalance;
        private int handedByUserId;
        private LocalDateTime requestedAt;

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
    }
}
