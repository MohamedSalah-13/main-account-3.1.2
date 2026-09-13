package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.UserShiftService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashierShiftScreenServiceTest {

    @Test
    void openShiftSnapshotCarriesOnePolicyAndOneLiveSummary() throws Exception {
        UserShiftService shifts = mock(UserShiftService.class);
        ShiftPolicyService policies = mock(ShiftPolicyService.class);
        CashierTreasuryAssignmentService assignments = mock(CashierTreasuryAssignmentService.class);
        UserShift open = new UserShift(7, 3);
        open.setStatus(ShiftStatus.OPEN);
        ShiftSummary summary = ShiftSummary.builder().openBalance(new BigDecimal("100.00")).build();
        List<CashierTreasuryChoice> choices = List.of(new CashierTreasuryChoice(3, "Till 3", true));

        when(assignments.availableTreasuries(7)).thenReturn(choices);
        when(shifts.getOpenShift(7)).thenReturn(open);
        when(shifts.getUserShifts(7)).thenReturn(List.of(open));
        when(shifts.getCurrentShiftSummary(7)).thenReturn(summary);
        when(policies.current()).thenReturn(new ShiftPolicy(
                ShiftMode.REQUIRED, true, false, BigDecimal.ZERO, true, false, false));
        when(policies.treasuries()).thenReturn(List.of(
                new TreasuryShiftPolicy(3, "Till 3", ShiftTrackingMode.RECONCILE)));

        var data = new CashierShiftScreenService(shifts, policies, assignments).load(7);

        assertSame(open, data.currentShift());
        assertSame(summary, data.summary());
        assertEquals(choices, data.treasuryChoices());
        assertTrue(data.hasClosableShift());
        assertTrue(data.reconcilesCash());
        assertTrue(data.blindClose());
        assertFalse(data.autoPrintZ());
    }

    @Test
    void screenWithoutCurrentShiftDoesNotCalculateAReconciliation() throws Exception {
        UserShiftService shifts = mock(UserShiftService.class);
        ShiftPolicyService policies = mock(ShiftPolicyService.class);
        CashierTreasuryAssignmentService assignments = mock(CashierTreasuryAssignmentService.class);
        when(assignments.availableTreasuries(7)).thenReturn(List.of());
        when(shifts.getOpenShift(7)).thenReturn(null);
        when(shifts.getUserShifts(7)).thenReturn(List.of());
        when(policies.current()).thenReturn(ShiftPolicy.DISABLED);

        var data = new CashierShiftScreenService(shifts, policies, assignments).load(7);

        assertNull(data.currentShift());
        assertNull(data.summary());
        assertFalse(data.hasClosableShift());
        assertFalse(data.reconcilesCash());
        verify(shifts, never()).getCurrentShiftSummary(7);
        verify(policies, never()).treasuries();
    }
}
