package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShiftCashAuditModelTest {

    @Test
    void filterNormalizesOptionalDocumentIdentityAndCapsResultSize() {
        var filter = new ShiftCashLedgerFilter(7, ShiftLedgerAction.DELETE,
                ShiftCashSource.SALES, 0, 99_999);

        assertEquals(null, filter.sourceId());
        assertEquals(ShiftCashLedgerFilter.DEFAULT_LIMIT, filter.limit());
        assertThrows(IllegalArgumentException.class,
                () -> ShiftCashLedgerFilter.forShift(0));
    }

    @Test
    void entryComputesSignedNetEffectAndNormalizesNullableText() {
        var entry = new ShiftCashLedgerEntry(1, 7, 4, 2, null, 3, null,
                ShiftCashSource.EXPENSE, 11, ShiftLedgerAction.DELETE,
                new BigDecimal("2.00"), new BigDecimal("5.50"), null,
                LocalDateTime.of(2026, 9, 2, 12, 0));

        assertEquals(new BigDecimal("-3.50"), entry.netDelta());
        assertEquals("", entry.treasuryName());
        assertEquals("", entry.actorUsername());
        assertEquals("", entry.reason());
    }
}
