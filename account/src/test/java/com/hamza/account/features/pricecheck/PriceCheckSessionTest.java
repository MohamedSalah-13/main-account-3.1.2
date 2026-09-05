package com.hamza.account.features.pricecheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that stops a slow answer to an earlier scan from overwriting the price the
 * customer is standing there reading.
 */
class PriceCheckSessionTest {

    @Test
    void theAnswerToTheNewestScanIsTheOneShown() {
        PriceCheckSession session = new PriceCheckSession();

        long scan = session.begin();

        assertTrue(session.isCurrent(scan));
    }

    @Test
    void anAnswerToASupersededScanIsDropped() {
        PriceCheckSession session = new PriceCheckSession();

        long first = session.begin();
        long second = session.begin();

        assertFalse(session.isCurrent(first), "the slow first answer must not be shown");
        assertTrue(session.isCurrent(second));
    }

    /** Back to waiting means the customer walked away: an answer still in flight is theirs. */
    @Test
    void cancellingLeavesNothingInFlightCurrent() {
        PriceCheckSession session = new PriceCheckSession();
        long scan = session.begin();

        session.cancel();

        assertFalse(session.isCurrent(scan));
    }
}
