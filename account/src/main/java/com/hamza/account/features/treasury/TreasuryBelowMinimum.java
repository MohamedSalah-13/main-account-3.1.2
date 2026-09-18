package com.hamza.account.features.treasury;

import java.math.BigDecimal;

/**
 * An active treasury holding less than the minimum set for it (V69).
 * <p>
 * A warning and never a refusal: a withdrawal is still refused only when it exceeds the balance
 * itself. The minimum is somebody's judgement of what a drawer needs for change, or a wallet for
 * the next supplier payment - and a judgement that stopped a sale would be switched off the first
 * busy morning.
 */
public record TreasuryBelowMinimum(int treasuryId, String name, BigDecimal balance, BigDecimal minimum) {

    public TreasuryBelowMinimum {
        balance = balance == null ? BigDecimal.ZERO : balance;
        minimum = minimum == null ? BigDecimal.ZERO : minimum;
    }

    /** How far under the minimum it is - what somebody has to move into it. */
    public BigDecimal shortBy() {
        return minimum.subtract(balance).max(BigDecimal.ZERO);
    }

    /**
     * The rule the query states in SQL, said once in Java so it can be tested: a minimum of zero
     * is "none set" - not "everything is low" - and a balance exactly at the minimum is enough.
     */
    public static boolean isBelow(BigDecimal balance, BigDecimal minimum) {
        return minimum != null && minimum.signum() > 0
                && (balance == null ? BigDecimal.ZERO : balance).compareTo(minimum) < 0;
    }
}
