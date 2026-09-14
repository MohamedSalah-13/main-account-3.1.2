package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.period.AccountingLock;
import com.hamza.account.period.PeriodLockService;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Where a close's variance goes when its date is locked, and who may post it later.
 * <p>
 * The session is never user 1, which bypasses every permission. The transaction is stubbed
 * away ({@code ConnectionManager.beginTransaction} answers null, which is how a joined
 * transaction looks), so what is checked is the decision inside it; that the rows land and
 * roll back together is {@code ShiftAccountingDatabaseAcceptanceTest}'s to prove.
 */
class ShiftVarianceSettlementServiceTest {

    private static final LocalDate LOCKED_UNTIL = LocalDate.of(2026, 9, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 9, 0);

    private final ShiftVarianceSettlementRepository repository = mock(ShiftVarianceSettlementRepository.class);
    private final ShiftCashHandoverService cash = mock(ShiftCashHandoverService.class);
    private final PeriodLockService lock = mock(PeriodLockService.class);
    private UserSessionContext session;
    private ShiftVarianceSettlementService service;

    @BeforeEach
    void setUp() {
        ServiceRegistry.register(PeriodLockService.class, lock);
        when(lock.current()).thenReturn(new AccountingLock(LOCKED_UNTIL, "", null, 2));
        signInWith(AppPermissions.SHIFT_FORCE_CLOSE);
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new ShiftVarianceSettlementService(repository, cash, session, clock);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.register(PeriodLockService.class, null);
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    private void signInWith(PermissionKey... granted) {
        session = new UserSessionContext();
        session.signIn(9, "supervisor", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    void aSquareDrawerNeitherDefersNorPosts() throws Exception {
        assertFalse(service.settleOrDefer(12, 3, new BigDecimal("400.001"), new BigDecimal("400.004"),
                7, LocalDateTime.of(2026, 9, 9, 22, 0)));
        verifyNoInteractions(repository, cash);
    }

    @Test
    void aVarianceDatedInsideTheLockIsDeferredAndNothingIsPosted() throws Exception {
        LocalDateTime closedAt = LOCKED_UNTIL.atTime(23, 30);

        assertTrue(service.settleOrDefer(12, 3, new BigDecimal("500"), new BigDecimal("480"), 7, closedAt));

        verify(repository).append(12, 3, new BigDecimal("500.00"), new BigDecimal("480.00"),
                new BigDecimal("-20.00"), LOCKED_UNTIL, 7, closedAt);
        verify(cash, never()).settleCloseVariance(anyInt(), anyInt(), any(), any(), anyInt(), any());
    }

    /**
     * V61's CHECK is {@code difference_amount = actual_balance - expected_balance} over the
     * stored, rounded balances. Subtracting first and rounding after gives 10.00 here where the
     * stored balances differ by 10.01, and MySQL would refuse the whole close.
     */
    @Test
    void theDeferredDifferenceIsExactlyTheDifferenceOfTheStoredBalances() throws Exception {
        LocalDateTime closedAt = LOCKED_UNTIL.atTime(12, 0);

        service.settleOrDefer(12, 3, new BigDecimal("10.004"), new BigDecimal("20.005"), 7, closedAt);

        verify(repository).append(12, 3, new BigDecimal("10.00"), new BigDecimal("20.01"),
                new BigDecimal("10.01"), LOCKED_UNTIL, 7, closedAt);
    }

    @Test
    void aVarianceDatedAfterTheLockIsPostedAtOnce() throws Exception {
        LocalDateTime closedAt = LOCKED_UNTIL.plusDays(1).atTime(0, 5);

        assertFalse(service.settleOrDefer(12, 3, new BigDecimal("500"), new BigDecimal("480"), 7, closedAt));

        verify(cash).settleCloseVariance(12, 3, new BigDecimal("500"), new BigDecimal("480"), 7, closedAt);
        verify(repository, never()).append(anyInt(), anyInt(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void withNothingClosedAVarianceIsPostedAtOnce() throws Exception {
        when(lock.current()).thenReturn(AccountingLock.OPEN);
        LocalDateTime closedAt = LocalDateTime.of(2020, 1, 1, 8, 0);

        assertFalse(service.settleOrDefer(12, 3, new BigDecimal("500"), new BigDecimal("510"), 7, closedAt));

        verify(cash).settleCloseVariance(12, 3, new BigDecimal("500"), new BigDecimal("510"), 7, closedAt);
    }

    @Test
    void listingAndSettlingNeedTheSupervisorPermission() {
        signInWith(AppPermissions.SHIFT_SELF_CLOSE);
        service = new ShiftVarianceSettlementService(repository, cash, session, Clock.systemDefaultZone());

        assertThrows(BusinessRuleException.class, () -> service.pending());
        assertThrows(BusinessRuleException.class, () -> service.settle(5));
        verifyNoInteractions(repository, cash);
    }

    @Test
    void aRequestNoLongerPendingIsRefused() throws Exception {
        try (MockedStatic<ConnectionManager> ignored = mockStatic(ConnectionManager.class)) {
            when(repository.findPendingForUpdate(5)).thenReturn(null);

            assertThrows(BusinessRuleException.class, () -> service.settle(5));
            verify(cash, never()).settleCloseVariance(anyInt(), anyInt(), any(), any(), anyInt(), any());
        }
    }

    /** Posted today, by whoever settles it - never back-dated into the period that was locked. */
    @Test
    void settlingPostsTheStoredBalancesOnTheDayItIsSettled() throws Exception {
        try (MockedStatic<ConnectionManager> ignored = mockStatic(ConnectionManager.class)) {
            when(repository.findPendingForUpdate(anyLong())).thenReturn(new ShiftVarianceSettlement(
                    5, 12, 3, "drawer", new BigDecimal("500.00"), new BigDecimal("480.00"),
                    new BigDecimal("-20.00"), LOCKED_UNTIL, 7, "cashier", LOCKED_UNTIL.atTime(23, 30)));

            assertEquals(12, service.settle(5));

            verify(cash).settleCloseVariance(12, 3, new BigDecimal("500.00"), new BigDecimal("480.00"), 9, NOW);
        }
    }
}
