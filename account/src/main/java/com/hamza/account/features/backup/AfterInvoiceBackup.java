package com.hamza.account.features.backup;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Runs the backups invoices ask for, as {@link BackupThrottle} decides.
 *
 * <p>One per process, not one per invoice screen: the four invoice families each build their own
 * post-save service, and a throttle each would let four dumps run side by side again. Runs happen
 * on one thread, so two can never overlap whatever the throttle is told.
 *
 * <p>A failure goes to {@link Outcome#failed}, not back to whoever saved the invoice. It used to
 * reach the cashier as an error dialog after every sale - for a folder that had become unwritable,
 * that is a dialog per customer about something the cashier cannot fix - and the sale it followed
 * had already been saved. The caller wires {@code failed} to a notification that folds repeats.
 */
public final class AfterInvoiceBackup {

    /** At most one after-invoice backup starts in this long. */
    public static final Duration GAP = Duration.ofMinutes(10);

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    public interface Outcome {
        void succeeded();

        void failed(Exception failure);
    }

    /** Where runs are carried out. One thread, so a run never overlaps another. */
    public interface Scheduler {
        void runNow(Runnable task);

        void runAfter(long delayMillis, Runnable task);
    }

    private final BackupThrottle throttle;
    private final Scheduler scheduler;
    private final LongSupplier clock;
    private final Action action;
    private final Outcome outcome;

    public AfterInvoiceBackup(Duration gap, Scheduler scheduler, LongSupplier clock, Action action, Outcome outcome) {
        this.throttle = new BackupThrottle(gap);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.action = Objects.requireNonNull(action, "action");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    /** An invoice has been saved. Returns at once; the backup, if any, runs later. */
    public void request() {
        BackupThrottle.Decision decision;
        synchronized (throttle) {
            decision = throttle.onRequest(clock.getAsLong());
        }
        dispatch(decision);
    }

    private void dispatch(BackupThrottle.Decision decision) {
        switch (decision.kind()) {
            case RUN_NOW -> scheduler.runNow(this::run);
            case RUN_AT -> scheduler.runAfter(Math.max(0, decision.at() - clock.getAsLong()), this::runWhenDue);
            case NOTHING -> {
            }
        }
    }

    private void runWhenDue() {
        synchronized (throttle) {
            throttle.onDue(clock.getAsLong());
        }
        run();
    }

    private void run() {
        try {
            action.run();
            outcome.succeeded();
        } catch (Exception failure) {
            outcome.failed(failure);
        } finally {
            BackupThrottle.Decision next;
            synchronized (throttle) {
                next = throttle.onFinish(clock.getAsLong());
            }
            dispatch(next);
        }
    }

    /** One daemon thread, so an unfinished backup never holds the application open. */
    public static Scheduler singleThread(String name) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
        return new Scheduler() {
            @Override
            public void runNow(Runnable task) {
                executor.execute(task);
            }

            @Override
            public void runAfter(long delayMillis, Runnable task) {
                executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
            }
        };
    }
}
