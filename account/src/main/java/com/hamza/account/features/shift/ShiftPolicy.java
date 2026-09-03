package com.hamza.account.features.shift;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;

/** Persisted application-wide shift policy. */
public record ShiftPolicy(
        ShiftMode mode,
        boolean blindClose,
        boolean autoPrintZ,
        BigDecimal varianceTolerance,
        boolean requireVarianceReason,
        boolean requireSupervisorApproval,
        boolean enforceTreasuryAssignments) {

    public static final ShiftPolicy DISABLED = new ShiftPolicy(
            ShiftMode.DISABLED, false, true, BigDecimal.ZERO, true, false, false);

    public ShiftPolicy {
        mode = mode == null ? ShiftMode.DISABLED : mode;
        varianceTolerance = MoneyMath.money(varianceTolerance == null ? BigDecimal.ZERO : varianceTolerance);
        if (varianceTolerance.signum() < 0) {
            throw new IllegalArgumentException("varianceTolerance must not be negative");
        }
    }
}
