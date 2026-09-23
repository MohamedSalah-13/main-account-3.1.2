package com.hamza.account.features.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupTrackerTest {

    /** A clock that moves only when told to. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T08:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final List<StartupSnapshot> published = new ArrayList<>();
    private final StartupTracker tracker = new StartupTracker(clock, published::add);

    private static double weightBefore(StartupStep step) {
        double done = 0;
        for (StartupStep earlier : StartupStep.values()) {
            if (earlier.ordinal() < step.ordinal()) {
                done += earlier.weight();
            }
        }
        return done / StartupStep.totalWeight();
    }

    @Test
    @DisplayName("a step writes a line, runs until the next begins, and says how long it took")
    void aStepIsALine() {
        tracker.begin(StartupStep.CONFIGURATION);
        clock.advance(2);
        tracker.begin(StartupStep.DATABASE);

        List<StartupLine> lines = tracker.snapshot().lines();
        assertEquals(2, lines.size());
        assertEquals("startup.step.configuration", lines.get(0).messageKey());
        assertEquals(StartupLine.State.DONE, lines.get(0).state());
        assertEquals(Duration.ofSeconds(2), lines.get(0).elapsed(clock.instant()));
        assertEquals(StartupLine.State.RUNNING, lines.get(1).state());
        assertEquals(StartupStep.DATABASE, tracker.snapshot().current());
    }

    @Test
    @DisplayName("a step passed over counts as done, so the bar only moves forward and ends at the end")
    void aSkippedStepCountsAsDone() {
        tracker.begin(StartupStep.DATABASE);
        tracker.begin(StartupStep.SETTINGS);

        assertEquals(weightBefore(StartupStep.SETTINGS), tracker.snapshot().progress(), 1e-9,
                "no backup and nothing to migrate is the ordinary start, and the bar is past both");
        assertEquals(2, tracker.snapshot().lines().size(), "a step passed over writes no line");

        tracker.finish();
        assertEquals(1.0, tracker.snapshot().progress());
        assertEquals(StartupSnapshot.Outcome.READY, tracker.snapshot().outcome());
        assertNull(tracker.snapshot().current());
    }

    @Test
    @DisplayName("the migrations move the bar through their own step, and never back")
    void theMigrationsMoveTheBar() {
        tracker.begin(StartupStep.MIGRATION);
        double start = tracker.snapshot().progress();
        tracker.fraction(0.5);
        double half = tracker.snapshot().progress();
        tracker.fraction(0.25);

        assertEquals(start + StartupStep.MIGRATION.weight() * 0.5 / StartupStep.totalWeight(), half, 1e-9);
        assertEquals(half, tracker.snapshot().progress(), 1e-9, "a late report of an earlier position is ignored");
    }

    @Test
    @DisplayName("each migration is a line under its step, and the one before it is done")
    void detailLines() {
        tracker.begin(StartupStep.MIGRATION);
        tracker.detail("startup.detail.migration", "V80", "1", "2");
        tracker.detail("startup.detail.migration", "V81", "2", "2");

        List<StartupLine> lines = tracker.snapshot().lines();
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).detail());
        assertEquals(List.of("V80", "1", "2"), lines.get(1).arguments());
        assertEquals(StartupLine.State.DONE, lines.get(1).state());
        assertEquals(StartupLine.State.RUNNING, lines.get(2).state());
        assertEquals(StartupLine.State.RUNNING, lines.get(0).state(), "the step runs while its migrations do");
    }

    @Test
    @DisplayName("a failure turns every running line red - the step and the migration it stopped on")
    void aFailure() {
        tracker.begin(StartupStep.MIGRATION);
        tracker.detail("startup.detail.migration", "V81", "1", "1");
        tracker.fail();

        StartupSnapshot snapshot = tracker.snapshot();
        assertEquals(StartupSnapshot.Outcome.FAILED, snapshot.outcome());
        assertEquals(StartupStep.MIGRATION, snapshot.current(), "the window says where it stopped");
        assertTrue(snapshot.lines().stream().allMatch(line -> line.state() == StartupLine.State.FAILED));

        tracker.begin(StartupStep.SERVICES);
        tracker.finish();
        assertEquals(StartupSnapshot.Outcome.FAILED, tracker.snapshot().outcome(), "nothing after a failure");
        assertEquals(2, tracker.snapshot().lines().size());
    }

    @Test
    @DisplayName("the steps run in order, once each")
    void stepsRunInOrder() {
        tracker.begin(StartupStep.SETTINGS);

        assertThrows(IllegalStateException.class, () -> tracker.begin(StartupStep.DATABASE));
        assertThrows(IllegalStateException.class, () -> tracker.begin(StartupStep.SETTINGS));
    }

    @Test
    @DisplayName("every change is handed to the window as a whole snapshot")
    void everyChangeIsPublished() {
        tracker.begin(StartupStep.CONFIGURATION);
        tracker.detail("startup.detail.pending", "3");
        tracker.fraction(0.5);
        tracker.finish();

        assertEquals(4, published.size());
        assertEquals(StartupSnapshot.Outcome.READY, published.getLast().outcome());
        assertFalse(published.getFirst().lines().isEmpty());
    }

    @Test
    @DisplayName("a detail before any step has nowhere to go and is dropped")
    void aDetailBeforeAnyStep() {
        tracker.detail("startup.detail.pending", "1");

        assertTrue(tracker.snapshot().lines().isEmpty());
        assertEquals(0.0, tracker.snapshot().progress());
    }

    /**
     * The window resolves these through a variable, where {@code MessageKeyArchitectureTest} cannot see
     * them - so they are checked against the three bundles here.
     */
    @Test
    @DisplayName("every key a start-up line can carry is in the three bundles")
    void everyKeyIsTranslated() throws Exception {
        List<String> keys = new ArrayList<>(List.of("startup.detail.pending", "startup.detail.migration",
                "startup.detail.definitions"));
        for (StartupStep step : StartupStep.values()) {
            keys.add(step.messageKey());
        }
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(in);
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }
}
