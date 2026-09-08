package com.hamza.account.party;

import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartyWriteGuardTest {

    @Test
    void appendsTheVersionReadByTheEditor() throws Exception {
        LocalDateTime version = LocalDateTime.of(2026, 9, 8, 5, 40, 1, 123_456_000);
        assertArrayEquals(new Object[]{"name", 7, Timestamp.valueOf(version)},
                PartyWriteGuard.withVersion(new Object[]{"name", 7}, version));
    }

    @Test
    void missingAndStaleVersionsAreRefused() {
        assertThrows(BusinessRuleException.class,
                () -> PartyWriteGuard.withVersion(new Object[]{"name", 7}, null));
        assertThrows(BusinessRuleException.class,
                () -> PartyWriteGuard.requireUpdated(0));
    }
}
