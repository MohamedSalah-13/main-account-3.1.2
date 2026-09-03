package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftCloseDecisionPolicyTest {
    @Test
    void requesterCannotDecideTheirOwnRequest() {
        assertEquals(ShiftCloseDecisionPolicy.Rejection.SAME_ACTOR,
                ShiftCloseDecisionPolicy.evaluate(request(7, 12), 7, 12));
    }

    @Test
    void changedJournalRequiresANewReconciliation() {
        assertEquals(ShiftCloseDecisionPolicy.Rejection.LEDGER_CHANGED,
                ShiftCloseDecisionPolicy.evaluate(request(7, 12), 8, 13));
    }

    @Test
    void anotherUserMayDecideAnUnchangedRequest() {
        assertEquals(ShiftCloseDecisionPolicy.Rejection.NONE,
                ShiftCloseDecisionPolicy.evaluate(request(7, 12), 8, 12));
    }

    private static ShiftCloseRequest request(int requester, long ledgerLastId) {
        BigDecimal zero = BigDecimal.ZERO;
        return new ShiftCloseRequest(1, 2, 3, "cashier", 1, "till", requester,
                "requester", LocalDateTime.now(), zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, 0, ledgerLastId, "reason");
    }
}
