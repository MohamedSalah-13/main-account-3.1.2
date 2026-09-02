package com.hamza.account.features.shift;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShiftGateTest {

    @Test
    void disabledModeNeverBlocksExistingWarehouseInstallations() throws Exception {
        ShiftGate gate = gate(ShiftMode.DISABLED, ShiftTrackingMode.RECONCILE, null);
        assertTrue(gate.requireCashAction(7, 3, money("100")).isEmpty());
    }

    @Test
    void disablingShiftsCannotErasePreviouslyShiftedHistory() {
        ShiftGate gate = gate(ShiftMode.DISABLED, ShiftTrackingMode.RECONCILE, null);
        assertThrows(BusinessRuleException.class,
                () -> gate.requireCashCorrection(7, 3, money("100"), 22));
    }

    @Test
    void untrackedTreasuryNeverRequiresAShift() throws Exception {
        ShiftGate gate = gate(ShiftMode.REQUIRED, ShiftTrackingMode.NONE, null);
        assertTrue(gate.requireCashAction(7, 3, money("100")).isEmpty());
    }

    @Test
    void untrackingATreasuryCannotErasePreviouslyShiftedHistory() {
        ShiftGate gate = gate(ShiftMode.OPTIONAL, ShiftTrackingMode.NONE, null);
        assertThrows(BusinessRuleException.class,
                () -> gate.requireCashCorrection(7, 3, money("100"), 22));
    }

    @Test
    void optionalModeAllowsButDoesNotInventAnAssignment() throws Exception {
        ShiftGate gate = gate(ShiftMode.OPTIONAL, ShiftTrackingMode.TRACK_ONLY, null);
        assertTrue(gate.requireCashAction(7, 3, money("100")).isEmpty());
    }

    @Test
    void optionalModeRequiresANewShiftWhenCorrectingShiftedHistory() {
        ShiftGate gate = gate(ShiftMode.OPTIONAL, ShiftTrackingMode.TRACK_ONLY, null);
        assertThrows(BusinessRuleException.class,
                () -> gate.requireCashCorrection(7, 3, money("100"), 22));
    }

    @Test
    void optionalModeAssignsHistoricalCorrectionToTheCurrentShift() throws Exception {
        UserShift current = new UserShift(7, 3);
        current.setId(41);
        ShiftGate gate = gate(ShiftMode.OPTIONAL, ShiftTrackingMode.TRACK_ONLY, current);
        assertEquals(41, gate.requireCashCorrection(7, 3, money("100"), 22).orElseThrow());
    }

    @Test
    void optionalModeStillAllowsCorrectionOfLegacyUnshiftedHistory() throws Exception {
        ShiftGate gate = gate(ShiftMode.OPTIONAL, ShiftTrackingMode.TRACK_ONLY, null);
        assertTrue(gate.requireCashCorrection(7, 3, money("100"), null).isEmpty());
    }

    @Test
    void requiredModeRejectsCashWithoutAMatchingOpenShift() {
        ShiftGate gate = gate(ShiftMode.REQUIRED, ShiftTrackingMode.RECONCILE, null);
        assertThrows(BusinessRuleException.class,
                () -> gate.requireCashAction(7, 3, money("100")));
    }

    @Test
    void requiredModeReturnsTheMatchingShiftId() throws Exception {
        UserShift shift = new UserShift(7, 3);
        shift.setId(41);
        ShiftGate gate = gate(ShiftMode.REQUIRED, ShiftTrackingMode.RECONCILE, shift);
        assertEquals(41, gate.requireCashAction(7, 3, money("100")).orElseThrow());
    }

    @Test
    void zeroCashDoesNotRequireAShift() throws Exception {
        ShiftGate gate = gate(ShiftMode.REQUIRED, ShiftTrackingMode.RECONCILE, null);
        assertTrue(gate.requireCashAction(7, 3, BigDecimal.ZERO).isEmpty());
    }

    @Test
    void receivingTransferUsesTheShiftOpenOnTheDestinationTreasury() throws Exception {
        UserShift destination = new UserShift(9, 4);
        destination.setId(77);
        ShiftGate gate = new ShiftGate(
                new FakePolicies(ShiftMode.REQUIRED, ShiftTrackingMode.RECONCILE),
                userId -> null, treasuryId -> destination);

        assertEquals(77, gate.requireTreasuryAction(4, money("50")).orElseThrow());
    }

    @Test
    void requiredReceivingTreasuryRejectsTransferWhenItHasNoOpenShift() {
        ShiftGate gate = new ShiftGate(
                new FakePolicies(ShiftMode.REQUIRED, ShiftTrackingMode.RECONCILE),
                userId -> null, treasuryId -> null);

        assertThrows(BusinessRuleException.class,
                () -> gate.requireTreasuryAction(4, money("50")));
    }

    @Test
    void optionalReceivingTreasuryAllowsAnUnassignedTransfer() throws Exception {
        ShiftGate gate = new ShiftGate(
                new FakePolicies(ShiftMode.OPTIONAL, ShiftTrackingMode.RECONCILE),
                userId -> null, treasuryId -> null);

        assertTrue(gate.requireTreasuryAction(4, money("50")).isEmpty());
    }

    private static ShiftGate gate(ShiftMode mode, ShiftTrackingMode tracking, UserShift open) {
        return new ShiftGate(new FakePolicies(mode, tracking), userId -> open);
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }

    private record FakePolicies(ShiftMode mode, ShiftTrackingMode tracking) implements ShiftPolicyRepository {
        public ShiftPolicy load() { return new ShiftPolicy(mode, false, true, BigDecimal.ZERO, true, false); }
        public List<TreasuryShiftPolicy> loadTreasuries() { return List.of(); }
        public ShiftTrackingMode trackingMode(int treasuryId) { return tracking; }
        public boolean hasOpenShifts() { return false; }
        public void save(ShiftPolicy policy) { }
        public void saveTreasury(TreasuryShiftPolicy policy) { }
    }
}
