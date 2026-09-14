package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.EmployeeEntryKind;
import com.hamza.account.features.employee.EmployeeLedgerEntry;
import com.hamza.account.features.employee.EmployeeLedgerService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Charging a shift's shortage to an employee: every refusal comes before anything is written.
 * <p>
 * The supervisor is user 9, never user 1 (which bypasses every permission); the cashier of the
 * shift is user 4. The transaction is stubbed away - see {@code ShiftVarianceSettlementServiceTest}.
 */
class ShiftShortageChargeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 15);
    private static final int CASHIER = 4;
    private static final int SUPERVISOR = 9;

    private final ShiftShortageChargeRepository repository = mock(ShiftShortageChargeRepository.class);
    private final EmployeeLedgerService ledger = mock(EmployeeLedgerService.class);
    private MockedStatic<ConnectionManager> transactions;
    private ShiftShortageChargeService service;

    @BeforeEach
    void setUp() throws Exception {
        transactions = mockStatic(ConnectionManager.class);
        service = serviceSignedInWith(AppPermissions.SHIFT_FORCE_CLOSE, AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, CASHIER, false, new BigDecimal("-55.254"), false));
        when(repository.activeEmployeeExists(30)).thenReturn(true);
        when(ledger.record(any())).thenReturn(77);
        when(repository.appendCharge(anyInt(), anyInt(), anyInt(), any(), any(), anyInt(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        transactions.close();
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    private ShiftShortageChargeService serviceSignedInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(SUPERVISOR, "supervisor", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new ShiftShortageChargeService(repository, ledger, session, clock);
    }

    @Test
    void chargingNeedsBothTheShiftAndTheEmployeeAccountPermission() {
        ShiftShortageChargeService withoutAdjust = serviceSignedInWith(AppPermissions.SHIFT_FORCE_CLOSE);
        assertThrows(BusinessRuleException.class, () -> withoutAdjust.charge(12, 30, "investigated"));

        ShiftShortageChargeService withoutForceClose =
                serviceSignedInWith(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        assertThrows(BusinessRuleException.class, () -> withoutForceClose.charge(12, 30, "investigated"));
        verifyNoInteractions(repository, ledger);
    }

    @Test
    void aReasonIsRequiredAndBounded() {
        assertThrows(UserValidationException.class, () -> service.charge(12, 30, "   "));
        assertThrows(UserValidationException.class, () -> service.charge(12, 30, null));
        assertThrows(UserValidationException.class,
                () -> service.charge(12, 30, "x".repeat(ShiftShortageChargeService.REASON_MAX + 1)));
        verifyNoInteractions(repository, ledger);
    }

    @Test
    void onlyAClosedShortageMayBeCharged() throws Exception {
        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, CASHIER, true, new BigDecimal("-10"), false));
        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "investigated"));

        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, CASHIER, false, new BigDecimal("10"), false));
        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "a surplus is nobody's debt"));

        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, CASHIER, false, new BigDecimal("-0.004"), false));
        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "rounds to nothing"));

        when(repository.findForUpdate(12)).thenReturn(null);
        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "no such shift"));
        verify(ledger, never()).record(any());
    }

    @Test
    void aShortageIsChargedOnce() throws Exception {
        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, CASHIER, false, new BigDecimal("-55"), true));

        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "again"));
        verify(ledger, never()).record(any());
    }

    @Test
    void theCashierMayNotApproveChargingTheirOwnShortage() throws Exception {
        when(repository.findForUpdate(12)).thenReturn(
                new ShiftShortageCase(12, SUPERVISOR, false, new BigDecimal("-55"), false));

        assertThrows(BusinessRuleException.class, () -> service.charge(12, 30, "my own drawer"));
        verify(ledger, never()).record(any());
    }

    @Test
    void theEmployeeMustBeActive() throws Exception {
        when(repository.activeEmployeeExists(31)).thenReturn(false);

        assertThrows(UserValidationException.class, () -> service.charge(12, 31, "investigated"));
        assertThrows(UserValidationException.class, () -> service.charge(12, 0, "investigated"));
        verify(ledger, never()).record(any());
    }

    /** The deduction goes through the ledger service, which validates it like any hand entry. */
    @Test
    void anApprovedChargeIsADeductionOfTheShortageLinkedToTheShift() throws Exception {
        assertEquals(77, service.charge(12, 30, "  counted twice, still short  "));

        ArgumentCaptor<EmployeeLedgerEntry> entry = ArgumentCaptor.forClass(EmployeeLedgerEntry.class);
        verify(ledger).record(entry.capture());
        assertEquals(30, entry.getValue().employeeId());
        assertEquals(EmployeeEntryKind.DEDUCTION, entry.getValue().kind());
        assertEquals(new BigDecimal("55.25"), entry.getValue().amount());
        assertEquals(NOW.toLocalDate(), entry.getValue().date());
        assertTrue(entry.getValue().notes().contains("counted twice, still short"));
        verify(repository).appendCharge(12, 30, 77, new BigDecimal("55.25"),
                "counted twice, still short", SUPERVISOR, NOW);
    }

    @Test
    void aLinkThatDidNotLandFailsTheWholeCharge() throws Exception {
        when(repository.appendCharge(anyInt(), anyInt(), anyInt(), any(), any(), anyInt(), any())).thenReturn(0);

        assertThrows(DaoException.class, () -> service.charge(12, 30, "investigated"));
    }
}
