package com.hamza.account.features.audit;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditLogReasonTest {

    @Test
    void trimsAndRequiresAUsefulAdministrativeReason() throws Exception {
        assertEquals("duplicate investigation", AuditLogService.requireReason("  duplicate investigation  "));
        assertThrows(UserValidationException.class, () -> AuditLogService.requireReason("   "));
        assertThrows(UserValidationException.class, () -> AuditLogService.requireReason("1234"));
        assertThrows(UserValidationException.class, () -> AuditLogService.requireReason("x".repeat(501)));
    }
}
