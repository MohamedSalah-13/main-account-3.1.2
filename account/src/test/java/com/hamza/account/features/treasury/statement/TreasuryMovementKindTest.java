package com.hamza.account.features.treasury.statement;

import com.hamza.account.treasury.MovementLabel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreasuryMovementKindTest {

    @Test
    void allTwelveDatabaseCodesRoundTrip() {
        assertEquals(12, TreasuryMovementKind.values().length);
        for (int code = 0; code <= 11; code++) {
            assertEquals(code, TreasuryMovementKind.fromCode(code).code());
        }
        assertThrows(IllegalArgumentException.class, () -> TreasuryMovementKind.fromCode(12));
    }

    @Test
    void everyStableKindStillNamesTheViewLabelItReplaces() {
        assertEquals(Set.copyOf(Arrays.asList(MovementLabel.values())),
                Arrays.stream(TreasuryMovementKind.values()).map(TreasuryMovementKind::storedLabel)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void onlyDocumentMovementsCanOpenAnInvoice() {
        assertEquals(4, Arrays.stream(TreasuryMovementKind.values())
                .filter(TreasuryMovementKind::isInvoice).count());
    }
}
