package com.hamza.account.service.version;

import com.hamza.account.features.startup.StartupProgress;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

/**
 * Tells the startup window which migration Flyway is applying, one line each, and how far through the
 * list it is. A versioned migration says its version ("V81"); a repeatable one - the views, the triggers,
 * the procedures - has none, and says that the definitions are being rebuilt.
 */
final class MigrationProgressCallback implements Callback {

    private final StartupProgress progress;
    private final int total;
    private int started;

    MigrationProgressCallback(StartupProgress progress, int total) {
        this.progress = progress;
        this.total = Math.max(total, 1);
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (event == Event.AFTER_EACH_MIGRATE) {
            progress.fraction((double) started / total);
            return;
        }
        started++;
        MigrationInfo migration = context.getMigrationInfo();
        String position = String.valueOf(Math.min(started, total));
        if (migration != null && migration.getVersion() != null) {
            progress.detail("startup.detail.migration", "V" + migration.getVersion().getVersion(),
                    position, String.valueOf(total));
        } else {
            progress.detail("startup.detail.definitions", position, String.valueOf(total));
        }
        progress.fraction((double) (started - 1) / total);
    }

    @Override
    public String getCallbackName() {
        return "startup-progress";
    }
}
