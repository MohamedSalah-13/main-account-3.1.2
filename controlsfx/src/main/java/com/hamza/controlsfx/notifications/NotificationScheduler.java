package com.hamza.controlsfx.notifications;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs the registered {@link NotificationSource}s off the FX thread and feeds
 * what they find to the {@link NotificationCenter}.
 * <p>
 * One daemon thread serves every source, so adding rules costs no threads and a
 * hung query delays the other rules rather than the UI. Daemon because the
 * application exits through {@code System.exit} in several places and a live
 * scheduler thread would otherwise keep the JVM up.
 * <p>
 * A source that throws is logged and stays scheduled: a database blip should not
 * silently retire a rule for the rest of the session.
 */
@Log4j2
public class NotificationScheduler {

    /** Anything shorter is a busy-loop against the database, not a schedule. */
    public static final Duration MINIMUM_INTERVAL = Duration.ofMinutes(1);

    private final NotificationCenter center;
    private final Map<String, NotificationSource> sources = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> handles = new ConcurrentHashMap<>();
    private final Map<String, Duration> intervalOverrides = new ConcurrentHashMap<>();

    private ScheduledExecutorService executor;

    public NotificationScheduler(@NotNull NotificationCenter center) {
        this.center = center;
    }

    /**
     * Registers a rule. Registering while running schedules it immediately, so
     * rules can be added after startup - by a plugin, or once a permission is known.
     */
    public synchronized void register(@NotNull NotificationSource source) {
        NotificationSource previous = sources.put(source.id(), source);
        if (previous != null) {
            log.warn("Notification source '{}' was replaced", source.id());
            cancel(source.id());
        }
        if (isRunning()) {
            schedule(source);
        }
    }

    public synchronized void unregister(@NotNull String sourceId) {
        sources.remove(sourceId);
        cancel(sourceId);
    }

    public List<NotificationSource> sources() {
        return new ArrayList<>(sources.values());
    }

    @Nullable
    public NotificationSource source(@NotNull String sourceId) {
        return sources.get(sourceId);
    }

    /**
     * How often the rule actually runs: the user's setting if there is one, the
     * rule's own suggestion otherwise, never below {@link #MINIMUM_INTERVAL}.
     * <p>
     * This is what the settings screen must display. Showing {@code source.interval()}
     * there would show the built-in default even where the user has overridden it.
     */
    @NotNull
    public Duration effectiveInterval(@NotNull String sourceId) {
        Duration override = intervalOverrides.get(sourceId);
        if (override != null) {
            return clampInterval(sourceId, override);
        }
        NotificationSource source = sources.get(sourceId);
        return source == null ? MINIMUM_INTERVAL : clampInterval(sourceId, source.interval());
    }

    /**
     * Changes how often a rule runs and puts the new schedule into effect at once,
     * rather than at the next restart. Passing {@code null} restores the rule's own
     * interval.
     */
    public synchronized void setInterval(@NotNull String sourceId, @Nullable Duration interval) {
        if (interval == null) {
            intervalOverrides.remove(sourceId);
        } else {
            intervalOverrides.put(sourceId, interval);
        }

        NotificationSource source = sources.get(sourceId);
        if (source == null || !isRunning()) {
            return;
        }
        // Re-scheduled from scratch: scheduleWithFixedDelay has no way to change the
        // delay of a task that is already queued.
        cancel(sourceId);
        schedule(source);
    }

    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        sources.values().forEach(this::schedule);
        log.info("Notification scheduler started with {} source(s)", sources.size());
    }

    public synchronized void stop() {
        handles.values().forEach(handle -> handle.cancel(false));
        handles.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Runs one rule now, on the scheduler thread. Used by the refresh button. */
    public void runNow(@NotNull String sourceId) {
        NotificationSource source = sources.get(sourceId);
        if (source == null || !isRunning()) {
            return;
        }
        executor.execute(() -> poll(source));
    }

    /** Re-checks every rule now, without disturbing their schedules. */
    public void runAllNow() {
        if (!isRunning()) {
            return;
        }
        sources.values().forEach(source -> executor.execute(() -> poll(source)));
    }

    private void schedule(NotificationSource source) {
        ScheduledFuture<?> handle = executor.scheduleWithFixedDelay(
                () -> poll(source),
                Math.max(0, source.initialDelay().toMillis()),
                effectiveInterval(source.id()).toMillis(),
                TimeUnit.MILLISECONDS);
        handles.put(source.id(), handle);
    }

    private Duration clampInterval(String sourceId, Duration interval) {
        if (interval == null || interval.compareTo(MINIMUM_INTERVAL) < 0) {
            log.warn("Interval {} for source '{}' is below the minimum; using {}",
                    interval, sourceId, MINIMUM_INTERVAL);
            return MINIMUM_INTERVAL;
        }
        return interval;
    }

    private void cancel(String sourceId) {
        ScheduledFuture<?> handle = handles.remove(sourceId);
        if (handle != null) {
            handle.cancel(false);
        }
    }

    private void poll(NotificationSource source) {
        if (!source.enabled() || center.policy().isMuted(source.category())) {
            return;
        }
        try {
            List<AppNotification> found = source.poll();
            if (!found.isEmpty()) {
                // Stamped here rather than in the rule, so routing a rule to Windows
                // works for every notification it produces without the rule knowing.
                found.forEach(notification -> notification.stampSourceId(source.id()));
                center.publishAll(found);
            }
        } catch (Exception e) {
            // Caught rather than left to the executor: an escaping exception cancels
            // a scheduleWithFixedDelay task permanently, so one failed query would
            // disable the rule until the next restart.
            log.error("Notification source '{}' failed", source.id(), e);
        }
    }
}
