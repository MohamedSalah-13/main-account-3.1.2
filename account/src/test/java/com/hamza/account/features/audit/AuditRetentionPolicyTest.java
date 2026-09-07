package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditRetentionPolicyTest {

    @Test
    void safeDefaultIsDisabledForOneYear() {
        AuditRetentionPolicy policy = AuditRetentionPolicy.disabled();

        assertFalse(policy.enabled());
        assertEquals(365, policy.days());
    }

    @Test
    void refusesDangerouslyShortOrUnboundedValues() {
        assertThrows(IllegalArgumentException.class, () -> new AuditRetentionPolicy(true, 29, null));
        assertThrows(IllegalArgumentException.class, () -> new AuditRetentionPolicy(true, 3651, null));
    }
}
