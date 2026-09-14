package com.hamza.account.features.backup;

import com.hamza.account.features.backup.RetentionPolicy.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyTest {

    private static final ZoneId ZONE = ZoneOffset.ofHours(3);

    private static Candidate at(LocalDateTime time) {
        return new Candidate("backup_" + time.toString().replace(':', '-') + ".enc",
                time.atZone(ZONE).toInstant().toEpochMilli());
    }

    /** An hourly schedule that has run, without a gap, from {@code first} to {@code last}. */
    private static List<Candidate> hourly(LocalDateTime first, LocalDateTime last) {
        List<Candidate> all = new ArrayList<>();
        for (LocalDateTime time = first; !time.isAfter(last); time = time.plusHours(1)) {
            all.add(at(time));
        }
        return all;
    }

    @Nested
    @DisplayName("tiered")
    class Tiered {

        private final RetentionPolicy.Tiered policy = new RetentionPolicy.Tiered(24, 7, 4, 12);
        // A Monday. The application's week starts on Saturday, so 2026-09-12 opens it.
        private final LocalDateTime newest = LocalDateTime.of(2026, 9, 14, 23, 0);
        private final List<Candidate> year = hourly(newest.minusDays(400), newest);
        private final Set<String> kept = policy.keep(year, ZONE);

        @Test
        @DisplayName("a year of hourly backups keeps a few dozen files, within the ceiling")
        void aYearIsAFewDozenFiles() {
            assertTrue(kept.size() <= policy.ceiling(), kept.size() + " > " + policy.ceiling());
            assertTrue(kept.size() >= 40, "the tiers overlap only a little on a gapless schedule: " + kept.size());
        }

        @Test
        @DisplayName("the last day is kept hour by hour")
        void theLastHoursAreAllThere() {
            for (int hour = 0; hour < 24; hour++) {
                assertTrue(kept.contains(at(newest.minusHours(hour)).name()), "hour -" + hour);
            }
            assertTrue(kept.contains(at(newest.minusHours(24)).name()),
                    "the 25th newest is yesterday 23:00 - kept, as yesterday's copy");
            assertFalse(kept.contains(at(newest.minusHours(25)).name()),
                    "yesterday 22:00 is neither in the last 24 nor the newest of its day");
        }

        @Test
        @DisplayName("one copy for each of the last seven days, the newest of that day")
        void oneADay() {
            for (int day = 1; day < 7; day++) {
                LocalDateTime lastOfDay = newest.minusDays(day).withHour(23);
                assertTrue(kept.contains(at(lastOfDay).name()), "day -" + day);
                assertFalse(kept.contains(at(lastOfDay.withHour(12)).name()), "midday of day -" + day);
            }
        }

        @Test
        @DisplayName("a copy from each of the last four weeks, weeks starting on Saturday")
        void oneAWeek() {
            // The newest of the week before 2026-09-12 is Friday 2026-09-11 23:00.
            assertTrue(kept.contains(at(LocalDateTime.of(2026, 9, 11, 23, 0)).name()));
            assertTrue(kept.contains(at(LocalDateTime.of(2026, 9, 4, 23, 0)).name()));
            assertTrue(kept.contains(at(LocalDateTime.of(2026, 8, 28, 23, 0)).name()));
            assertFalse(kept.contains(at(LocalDateTime.of(2026, 8, 21, 23, 0)).name()),
                    "a fifth week back is not kept for its week - and 21 August is not the end of a month");
        }

        @Test
        @DisplayName("a copy from each of the last twelve months, the month's last")
        void oneAMonth() {
            assertTrue(kept.contains(at(LocalDateTime.of(2026, 8, 31, 23, 0)).name()));
            assertTrue(kept.contains(at(LocalDateTime.of(2026, 1, 31, 23, 0)).name()));
            assertTrue(kept.contains(at(LocalDateTime.of(2025, 10, 31, 23, 0)).name()), "eleven months back");
            assertFalse(kept.contains(at(LocalDateTime.of(2025, 9, 30, 23, 0)).name()), "twelve months back is past the tier");
        }

        @Test
        @DisplayName("counted from the newest backup, so a schedule that stopped long ago is not emptied")
        void countedFromTheNewestBackup() {
            LocalDateTime stopped = LocalDateTime.of(2024, 3, 1, 12, 0);
            List<Candidate> old = hourly(stopped.minusDays(10), stopped);

            Set<String> keptOld = policy.keep(old, ZONE);

            assertTrue(keptOld.size() >= 24 + 6, "the last hours and days before it stopped: " + keptOld.size());
        }

        @Test
        void nothingToKeepFromNothing() {
            assertTrue(policy.keep(List.of(), ZONE).isEmpty());
        }

        @Test
        void refusesNonsense() {
            assertThrows(IllegalArgumentException.class, () -> new RetentionPolicy.Tiered(0, 7, 4, 12));
            assertThrows(IllegalArgumentException.class, () -> new RetentionPolicy.Tiered(24, -1, 4, 12));
        }
    }

    @Test
    @DisplayName("keep newest keeps exactly that many, the newest")
    void keepNewest() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<Candidate> files = List.of(at(base), at(base.plusMinutes(1)), at(base.plusMinutes(2)));

        assertEquals(Set.of(at(base.plusMinutes(1)).name(), at(base.plusMinutes(2)).name()),
                new RetentionPolicy.KeepNewest(2).keep(files, ZONE));
        assertThrows(IllegalArgumentException.class, () -> new RetentionPolicy.KeepNewest(0));
    }

    @Test
    void keepAllKeepsAll() {
        LocalDateTime base = LocalDateTime.of(2020, 1, 1, 0, 0);
        List<Candidate> files = hourly(base, base.plusDays(40));

        assertEquals(files.size(), new RetentionPolicy.KeepAll().keep(files, ZONE).size());
    }
}
