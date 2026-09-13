package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.UserShiftService;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** Builds one consistent, JavaFX-free snapshot for the cashier shift screen. */
public final class CashierShiftScreenService {
    private final UserShiftService shifts;
    private final ShiftPolicyService policies;
    private final CashierTreasuryAssignmentService assignments;

    public CashierShiftScreenService(UserShiftService shifts, ShiftPolicyService policies,
                                     CashierTreasuryAssignmentService assignments) {
        this.shifts = shifts;
        this.policies = policies;
        this.assignments = assignments;
    }

    public CashierShiftScreenData load(int userId) throws DaoException {
        List<CashierTreasuryChoice> treasuryChoices = assignments.availableTreasuries(userId);
        UserShift currentShift = shifts.getOpenShift(userId);
        List<UserShift> history = shifts.getUserShifts(userId);
        ShiftPolicy policy = policies.current();
        ShiftTrackingMode trackingMode = currentShift == null
                ? ShiftTrackingMode.NONE
                : policies.treasuries().stream()
                .filter(item -> item.treasuryId() == currentShift.getTreasuryId())
                .map(TreasuryShiftPolicy::trackingMode)
                .findFirst().orElse(ShiftTrackingMode.NONE);
        ShiftSummary summary = currentShift != null && currentShift.getStatus() == ShiftStatus.OPEN
                ? shifts.getCurrentShiftSummary(userId)
                : null;
        return new CashierShiftScreenData(
                treasuryChoices, currentShift, history, summary,
                policy.blindClose(), policy.autoPrintZ(), trackingMode);
    }

    public record CashierShiftScreenData(
            List<CashierTreasuryChoice> treasuryChoices,
            UserShift currentShift,
            List<UserShift> history,
            ShiftSummary summary,
            boolean blindClose,
            boolean autoPrintZ,
            ShiftTrackingMode trackingMode) {

        public CashierShiftScreenData {
            treasuryChoices = List.copyOf(treasuryChoices);
            history = List.copyOf(history);
        }

        public boolean hasClosableShift() {
            return currentShift != null && currentShift.getStatus() == ShiftStatus.OPEN;
        }

        public boolean reconcilesCash() {
            return trackingMode == ShiftTrackingMode.RECONCILE;
        }
    }
}
