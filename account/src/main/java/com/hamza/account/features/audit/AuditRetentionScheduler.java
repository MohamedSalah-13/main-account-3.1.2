package com.hamza.account.features.audit;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Once-per-session daemon that checks the shop-wide policy without blocking login. */
@Log4j2
public final class AuditRetentionScheduler {

    private static ScheduledExecutorService executor;

    private AuditRetentionScheduler() {
    }

    public static synchronized void start(AuditRetentionService service) {
        stop();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "audit-retention");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                int removed = service.runAutomaticIfDue();
                if (removed > 0) log.info("Audit retention removed {} expired rows", removed);
            } catch (Exception error) {
                log.warn("Automatic audit retention failed", error);
            }
        }, 1, 24 * 60, TimeUnit.MINUTES);
    }

    public static synchronized void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
