package com.hamza.account.features.startup;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns the start-up's calls into the lines of its log and the position of its bar.
 *
 * <p>Called from the work's own thread and read by the window's; every change produces a whole
 * {@link StartupSnapshot} and hands it to the sink, which is where the window marshals to its own thread.
 * Nothing here knows JavaFX, so the rules the screen shows are tested without it.</p>
 *
 * <p>The bar only moves forward. A step is worth its weight whether it ran or was passed over, the running
 * step adds its weight times {@link #fraction}, and the end is the end: {@link #finish} fills the bar
 * however many steps were never needed.</p>
 */
public final class StartupTracker implements StartupProgress {

    private final Clock clock;
    private final Consumer<StartupSnapshot> sink;
    private final List<StartupLine> lines = new ArrayList<>();

    private StartupStep current;
    private double fraction;
    private StartupSnapshot.Outcome outcome = StartupSnapshot.Outcome.RUNNING;

    public StartupTracker(Clock clock, Consumer<StartupSnapshot> sink) {
        this.clock = clock;
        this.sink = sink;
    }

    @Override
    public synchronized void begin(StartupStep step) {
        if (outcome != StartupSnapshot.Outcome.RUNNING) {
            return;
        }
        if (current != null && step.ordinal() <= current.ordinal()) {
            throw new IllegalStateException("Start-up steps run in order: " + step + " after " + current);
        }
        Instant now = clock.instant();
        endRunning(StartupLine.State.DONE, now);
        current = step;
        fraction = 0;
        lines.add(new StartupLine(step.messageKey(), List.of(), StartupLine.State.RUNNING, false, now, null));
        publish();
    }

    @Override
    public synchronized void detail(String messageKey, String... arguments) {
        if (outcome != StartupSnapshot.Outcome.RUNNING || current == null) {
            return;
        }
        Instant now = clock.instant();
        endRunningDetail(now);
        lines.add(new StartupLine(messageKey, List.of(arguments), StartupLine.State.RUNNING, true, now, null));
        publish();
    }

    @Override
    public synchronized void fraction(double done) {
        if (outcome != StartupSnapshot.Outcome.RUNNING) {
            return;
        }
        fraction = Math.max(fraction, Math.min(1, Math.max(0, done)));
        publish();
    }

    @Override
    public synchronized void finish() {
        if (outcome != StartupSnapshot.Outcome.RUNNING) {
            return;
        }
        endRunning(StartupLine.State.DONE, clock.instant());
        outcome = StartupSnapshot.Outcome.READY;
        current = null;
        publish();
    }

    @Override
    public synchronized void fail() {
        if (outcome != StartupSnapshot.Outcome.RUNNING) {
            return;
        }
        endRunning(StartupLine.State.FAILED, clock.instant());
        outcome = StartupSnapshot.Outcome.FAILED;
        publish();
    }

    public synchronized StartupSnapshot snapshot() {
        return new StartupSnapshot(lines, current, progress(), outcome);
    }

    /** The finished steps' weights, the passed-over ones included, plus the running step's share. */
    double progress() {
        if (outcome == StartupSnapshot.Outcome.READY) {
            return 1;
        }
        if (current == null) {
            return 0;
        }
        int done = 0;
        for (StartupStep step : StartupStep.values()) {
            if (step.ordinal() < current.ordinal()) {
                done += step.weight();
            }
        }
        return (done + current.weight() * fraction) / StartupStep.totalWeight();
    }

    private void endRunning(StartupLine.State end, Instant at) {
        for (int index = 0; index < lines.size(); index++) {
            StartupLine line = lines.get(index);
            if (line.state() == StartupLine.State.RUNNING) {
                lines.set(index, line.ended(end, at));
            }
        }
    }

    private void endRunningDetail(Instant at) {
        for (int index = 0; index < lines.size(); index++) {
            StartupLine line = lines.get(index);
            if (line.detail() && line.state() == StartupLine.State.RUNNING) {
                lines.set(index, line.ended(StartupLine.State.DONE, at));
            }
        }
    }

    private void publish() {
        sink.accept(snapshot());
    }
}
