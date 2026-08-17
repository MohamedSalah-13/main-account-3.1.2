package com.hamza.account.features.returns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnReasonTest {

    @ParameterizedTest
    @EnumSource(ReturnReason.class)
    void storedValueRoundTripsThroughFromStoredValue(ReturnReason reason) {
        assertEquals(reason, ReturnReason.fromStoredValue(reason.storedValue()));
    }

    @ParameterizedTest
    @EnumSource(ReturnReason.class)
    void everyNameFitsTheColumnV16Added(ReturnReason reason) {
        // total_sales_re.return_reason / total_buy_re.return_reason are VARCHAR(32).
        assertTrue(reason.storedValue().length() <= 32, reason.storedValue());
    }

    @ParameterizedTest
    @EnumSource(ReturnReason.class)
    void everyReasonResolvesANonBlankLabel(ReturnReason reason) {
        assertFalse(reason.label().isBlank());
    }

    @Test
    void aBlankOrMissingStoredValueIsNoReason() {
        assertNull(ReturnReason.fromStoredValue(null));
        assertNull(ReturnReason.fromStoredValue(""));
        assertNull(ReturnReason.fromStoredValue("   "));
    }
}
