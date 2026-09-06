package com.hamza.account.features.workstation;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Says "this machine is still here" once a minute, on a daemon thread.
 *
 * <p>Started after a user signs in rather than from the bootstrap, for the same reason the
 * notifications are: the row records who is at the machine, and at bootstrap nobody is.
 * Re-entering it after a logout and a second login replaces the schedule rather than
 * adding one.
 */
@Log4j2
public final class WorkstationHeartbeat {

    private static final long EVERY_MINUTES = 1;

    private static ScheduledExecutorService scheduler;

    private WorkstationHeartbeat() {
    }

    public static synchronized void start(String databaseVersion) {
        stop();
        WorkstationService service = new WorkstationService();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workstation-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        // Immediately, then on the minute: a machine that is opened and closed inside a
        // minute is still a machine somebody has to be able to see.
        scheduler.scheduleAtFixedRate(() -> service.reportIn(databaseVersion),
                0, EVERY_MINUTES, TimeUnit.MINUTES);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
