package com.hamza.account.features.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The throttle's decisions carried out: a scheduler that runs nothing until told to. */
class AfterInvoiceBackupTest {

    private static final long MINUTE = 60_000;

    private final AtomicLong now = new AtomicLong();
    private final List<Runnable> immediate = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final List<Runnable> delayed = new ArrayList<>();
    private final AtomicInteger backups = new AtomicInteger();
    private final List<Exception> failures = new ArrayList<>();
    private final AtomicInteger successes = new AtomicInteger();

    private final AfterInvoiceBackup.Scheduler scheduler = new AfterInvoiceBackup.Scheduler() {
        @Override
        public void runNow(Runnable task) {
            immediate.add(task);
        }

        @Override
        public void runAfter(long delayMillis, Runnable task) {
            delays.add(delayMillis);
            delayed.add(task);
        }
    };

    private AfterInvoiceBackup backup(AfterInvoiceBackup.Action action) {
        return new AfterInvoiceBackup(Duration.ofMinutes(10), scheduler, now::get, action,
                new AfterInvoiceBackup.Outcome() {
                    @Override
                    public void succeeded() {
                        successes.incrementAndGet();
                    }

                    @Override
                    public void failed(Exception failure) {
                        failures.add(failure);
                    }
                });
    }

    @Test
    @DisplayName("ten invoices in a busy minute take two dumps: one at once, one after the gap")
    void aBusyMinuteIsTwoDumps() {
        AfterInvoiceBackup backups = backup(this.backups::incrementAndGet);

        backups.request();
        for (int i = 1; i < 10; i++) {
            now.set(i * 5_000L);
            backups.request();
        }
        assertEquals(1, immediate.size(), "one dump starts at once, however many invoices follow");

        now.set(2 * MINUTE);
        immediate.removeFirst().run();

        assertEquals(1, this.backups.get());
        assertEquals(List.of(8 * MINUTE), delays, "the owed run waits out the rest of the gap");

        now.set(10 * MINUTE);
        delayed.removeFirst().run();

        assertEquals(2, this.backups.get());
        assertTrue(immediate.isEmpty() && delayed.isEmpty(), "nothing is owed after the covering run");
        assertEquals(2, successes.get());
    }

    @Test
    @DisplayName("a failure is reported, and the next invoice can still be backed up")
    void aFailureIsReportedAndReleases() {
        AfterInvoiceBackup backups = backup(() -> {
            throw new IllegalStateException("folder is read-only");
        });

        backups.request();
        immediate.removeFirst().run();

        assertEquals(1, failures.size());
        assertEquals("folder is read-only", failures.getFirst().getMessage());

        now.set(10 * MINUTE);
        backups.request();
        assertEquals(1, immediate.size());
    }

    @Test
    @DisplayName("the shipped gap is ten minutes")
    void gapIsTenMinutes() {
        assertEquals(Duration.ofMinutes(10), AfterInvoiceBackup.GAP);
    }
}
