package com.hamza.account.features.backup;

import com.hamza.account.features.backup.BackupThrottle.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackupThrottleTest {

    private static final long MINUTE = 60_000;
    private final BackupThrottle throttle = new BackupThrottle(Duration.ofMinutes(10));

    @Test
    @DisplayName("the first invoice is backed up at once")
    void firstRequestRunsNow() {
        assertEquals(Decision.Kind.RUN_NOW, throttle.onRequest(0).kind());
    }

    @Test
    @DisplayName("an invoice saved while a backup runs gets one more run, after the gap")
    void aRequestDuringARunIsOwedOneMore() {
        throttle.onRequest(0);
        assertEquals(Decision.Kind.NOTHING, throttle.onRequest(1 * MINUTE).kind());
        assertEquals(Decision.Kind.NOTHING, throttle.onRequest(2 * MINUTE).kind());

        Decision next = throttle.onFinish(3 * MINUTE);

        assertEquals(Decision.Kind.RUN_AT, next.kind());
        assertEquals(10 * MINUTE, next.at(), "the gap is counted from when the last run started");
    }

    @Test
    @DisplayName("a burst of invoices inside the gap adds one run, not one each")
    void aBurstIsOneRun() {
        throttle.onRequest(0);
        throttle.onFinish(1 * MINUTE);

        Decision first = throttle.onRequest(2 * MINUTE);
        assertEquals(Decision.Kind.RUN_AT, first.kind());
        assertEquals(10 * MINUTE, first.at());
        for (int minute = 3; minute < 10; minute++) {
            assertEquals(Decision.Kind.NOTHING, throttle.onRequest(minute * MINUTE).kind(),
                    "already covered by the run scheduled for minute ten");
        }
    }

    @Test
    @DisplayName("the scheduled run covers requests made before it starts, and none is owed after it")
    void theScheduledRunCoversWhatCameBefore() {
        throttle.onRequest(0);
        throttle.onFinish(1 * MINUTE);
        throttle.onRequest(2 * MINUTE);
        throttle.onRequest(5 * MINUTE);

        throttle.onDue(10 * MINUTE);

        assertEquals(Decision.Kind.NOTHING, throttle.onFinish(11 * MINUTE).kind());
    }

    @Test
    @DisplayName("a request during the scheduled run, once it has started, is owed another")
    void aRequestDuringTheScheduledRunIsOwed() {
        throttle.onRequest(0);
        throttle.onFinish(1 * MINUTE);
        throttle.onRequest(2 * MINUTE);
        throttle.onDue(10 * MINUTE);

        assertEquals(Decision.Kind.NOTHING, throttle.onRequest(10 * MINUTE + 1).kind());
        Decision next = throttle.onFinish(11 * MINUTE);

        assertEquals(Decision.Kind.RUN_AT, next.kind());
        assertEquals(20 * MINUTE, next.at());
    }

    @Test
    @DisplayName("after a quiet spell longer than the gap, an invoice is backed up at once again")
    void afterTheGapItRunsNow() {
        throttle.onRequest(0);
        throttle.onFinish(1 * MINUTE);

        assertEquals(Decision.Kind.RUN_NOW, throttle.onRequest(10 * MINUTE).kind(), "exactly the gap is due");
    }

    @Test
    @DisplayName("a run that took longer than the gap is followed at once when something is owed")
    void aLongRunIsFollowedAtOnce() {
        throttle.onRequest(0);
        throttle.onRequest(1 * MINUTE);

        assertEquals(Decision.Kind.RUN_NOW, throttle.onFinish(12 * MINUTE).kind());
    }

    @Test
    @DisplayName("run now counts as started, so a second request before the thread picks it up adds nothing")
    void runNowIsAlreadyStarted() {
        assertEquals(Decision.Kind.RUN_NOW, throttle.onRequest(0).kind());

        assertEquals(Decision.Kind.NOTHING, throttle.onRequest(0).kind());
    }

    @Test
    @DisplayName("a failed run releases the throttle the same as a good one")
    void finishingIsFinishingWhateverHappened() {
        throttle.onRequest(0);
        throttle.onFinish(0);

        assertEquals(Decision.Kind.RUN_NOW, throttle.onRequest(10 * MINUTE).kind());
    }

    @Test
    void refusesANegativeGap() {
        assertThrows(IllegalArgumentException.class, () -> new BackupThrottle(Duration.ofMinutes(-1)));
    }
}
