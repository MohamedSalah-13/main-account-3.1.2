package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ShiftPolicyTest {
    @Test
    void nullValuesKeepTheBackwardsCompatibleDisabledDefault() {
        ShiftPolicy policy = new ShiftPolicy(null, false, true, null, true, false);
        assertEquals(ShiftMode.DISABLED, policy.mode());
        assertEquals(0, policy.varianceTolerance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void negativeToleranceIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new ShiftPolicy(ShiftMode.REQUIRED, false, true,
                        new BigDecimal("-0.01"), true, false));
    }
}
