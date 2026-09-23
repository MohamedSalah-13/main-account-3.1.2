package com.hamza.account.service.version;

import com.hamza.account.features.startup.StartupProgress;
import com.hamza.account.features.startup.StartupStep;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationProgressCallbackTest {

    /** What the callback told the window, in order. */
    private static final class Recording implements StartupProgress {
        final List<String> said = new ArrayList<>();

        @Override
        public void begin(StartupStep step) {
            said.add("begin " + step);
        }

        @Override
        public void detail(String messageKey, String... arguments) {
            said.add(messageKey + " " + String.join(",", arguments));
        }

        @Override
        public void fraction(double done) {
            said.add("fraction " + done);
        }

        @Override
        public void finish() {
            said.add("finish");
        }

        @Override
        public void fail() {
            said.add("fail");
        }
    }

    private static Context migrating(String version) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getVersion()).thenReturn(version == null ? null : MigrationVersion.fromVersion(version));
        Context context = mock(Context.class);
        when(context.getMigrationInfo()).thenReturn(info);
        return context;
    }

    @Test
    @DisplayName("each migration is a line with its version and its place in the list")
    void eachMigrationIsALine() {
        Recording progress = new Recording();
        MigrationProgressCallback callback = new MigrationProgressCallback(progress, 2);

        callback.handle(Event.BEFORE_EACH_MIGRATE, migrating("80"));
        callback.handle(Event.AFTER_EACH_MIGRATE, migrating("80"));
        callback.handle(Event.BEFORE_EACH_MIGRATE, migrating(null));
        callback.handle(Event.AFTER_EACH_MIGRATE, migrating(null));

        assertEquals(List.of(
                "startup.detail.migration V80,1,2", "fraction 0.0", "fraction 0.5",
                "startup.detail.definitions 2,2", "fraction 0.5", "fraction 1.0"), progress.said);
    }

    @Test
    @DisplayName("it listens to each migration and to nothing else Flyway announces")
    void supportsEachMigrationOnly() {
        MigrationProgressCallback callback = new MigrationProgressCallback(new Recording(), 1);

        assertTrue(callback.supports(Event.BEFORE_EACH_MIGRATE, null));
        assertTrue(callback.supports(Event.AFTER_EACH_MIGRATE, null));
        assertFalse(callback.supports(Event.BEFORE_MIGRATE, null));
        assertFalse(callback.supports(Event.AFTER_EACH_MIGRATE_ERROR, null));
    }
}
