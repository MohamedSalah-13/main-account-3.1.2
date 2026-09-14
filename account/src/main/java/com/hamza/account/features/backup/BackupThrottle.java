package com.hamza.account.features.backup;

import java.time.Duration;
import java.util.Objects;

/**
 * When a backup asked for after an invoice actually runs.
 *
 * <p>"Back up after saving an invoice" used to start a complete {@code mysqldump} for every
 * invoice, on a pool that did not wait for the one before: ten sales in a minute were ten full
 * dumps at once, each several seconds long. What the setting is for is that a sale saved is
 * soon in a backup, and one dump covers every sale committed before it starts - so the right
 * number of dumps for ten quick sales is one, taken after the last of them.
 *
 * <p>So: one run at a time, and at most one start per gap. A request inside the gap, or while a
 * run is going, is not dropped - it is remembered, and one more run follows once the gap has
 * passed, so the last invoice of a busy spell is always covered by a dump that started after it
 * was saved. Many requests in that window still add one run, not many.
 *
 * <p>A {@link Decision.Kind#RUN_NOW} answer has already counted the run as started. Were that
 * left to the thread that picks the run up, a second request arriving in between would find
 * nothing running and ask for a second run of its own.
 *
 * <p>It holds no thread and no clock: every answer is a function of the times it is given, which
 * is what lets it be checked without waiting ten minutes. {@link AfterInvoiceBackup} does the
 * scheduling and calls these methods under its own lock.
 */
public final class BackupThrottle {

    /** What to do next. {@code at} is when to start, meaningful only for {@link Kind#RUN_AT}. */
    public record Decision(Kind kind, long at) {
        public enum Kind { NOTHING, RUN_NOW, RUN_AT }

        static final Decision NOTHING = new Decision(Kind.NOTHING, 0);
        static final Decision RUN_NOW = new Decision(Kind.RUN_NOW, 0);

        static Decision runAt(long at) {
            return new Decision(Kind.RUN_AT, at);
        }
    }

    private final long gapMillis;

    private boolean running;
    private boolean scheduled;
    private boolean pending;
    private boolean everStarted;
    private long lastStart;

    public BackupThrottle(Duration gap) {
        Objects.requireNonNull(gap, "gap");
        if (gap.isNegative()) {
            throw new IllegalArgumentException("gap must not be negative");
        }
        this.gapMillis = gap.toMillis();
    }

    /** A backup was asked for at {@code now}. */
    public Decision onRequest(long now) {
        if (running) {
            // The run going now started before this request, so it may not include it.
            pending = true;
            return Decision.NOTHING;
        }
        if (scheduled) {
            // A run that starts later than this request will include it.
            return Decision.NOTHING;
        }
        return startOrSchedule(now);
    }

    /** The run a {@link Decision.Kind#RUN_AT} answer scheduled has come due at {@code now}. */
    public void onDue(long now) {
        scheduled = false;
        start(now);
    }

    /** The run that started last has finished, well or badly, at {@code now}. */
    public Decision onFinish(long now) {
        running = false;
        if (!pending) {
            return Decision.NOTHING;
        }
        pending = false;
        return startOrSchedule(now);
    }

    private Decision startOrSchedule(long now) {
        if (!everStarted || now >= lastStart + gapMillis) {
            start(now);
            return Decision.RUN_NOW;
        }
        scheduled = true;
        return Decision.runAt(lastStart + gapMillis);
    }

    private void start(long now) {
        running = true;
        pending = false;
        everStarted = true;
        lastStart = now;
    }
}
