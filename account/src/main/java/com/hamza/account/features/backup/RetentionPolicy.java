package com.hamza.account.features.backup;

import com.hamza.account.features.party.statement.StatementPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which backups of one kind to keep.
 *
 * <p>Every policy is a pure choice over names and times, so what gets deleted is decided by
 * something a test can ask about - deleting is the one thing a backup system must not get wrong.
 */
public sealed interface RetentionPolicy {

    /** A backup file as retention sees it: its name, and when it was written. */
    record Candidate(String name, long modifiedMillis) {
    }

    /** The names of {@code candidates} to keep; every other one is deleted. */
    Set<String> keep(List<Candidate> candidates, ZoneId zone);

    /** Nothing is ever deleted. */
    record KeepAll() implements RetentionPolicy {
        @Override
        public Set<String> keep(List<Candidate> candidates, ZoneId zone) {
            Set<String> all = new HashSet<>();
            candidates.forEach(candidate -> all.add(candidate.name()));
            return all;
        }
    }

    /** The newest {@code count}. */
    record KeepNewest(int count) implements RetentionPolicy {
        public KeepNewest {
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1");
            }
        }

        @Override
        public Set<String> keep(List<Candidate> candidates, ZoneId zone) {
            Set<String> kept = new LinkedHashSet<>();
            newestFirst(candidates).stream().limit(count).forEach(candidate -> kept.add(candidate.name()));
            return kept;
        }
    }

    /**
     * The newest {@code recent}, then the newest of each of the last {@code days} days, of each of
     * the last {@code weeks} weeks and of each of the last {@code months} months - a backup counted
     * once however many of those it answers for.
     *
     * <p>"Keep the newest thirty" answered one question, "what did we have in the last few hours",
     * and on an hourly schedule nothing older than a day and a bit survived it. The mistake found
     * a week later - a price list overwritten, a customer's movements deleted - had no copy from
     * before it. This keeps the same few hours and adds a copy a day for a week, a copy a week for
     * a month and a copy a month for a year, for a handful more files.
     *
     * <p>The periods are counted back from the newest backup, not from the clock: retention runs
     * right after a backup succeeds, when the two are the same, and a folder whose schedule
     * stopped months ago must not be emptied down to nothing by the first backup that runs again.
     * A week starts on {@link StatementPeriod#FIRST_DAY_OF_WEEK}, the one week the application has.
     */
    record Tiered(int recent, int days, int weeks, int months) implements RetentionPolicy {
        public Tiered {
            if (recent < 1 || days < 0 || weeks < 0 || months < 0) {
                throw new IllegalArgumentException("recent must be at least 1, the others not negative");
            }
        }

        @Override
        public Set<String> keep(List<Candidate> candidates, ZoneId zone) {
            List<Candidate> ordered = newestFirst(candidates);
            Set<String> kept = new LinkedHashSet<>();
            if (ordered.isEmpty()) {
                return kept;
            }
            ordered.stream().limit(recent).forEach(candidate -> kept.add(candidate.name()));

            LocalDate newest = dateOf(ordered.getFirst(), zone);
            LocalDate newestWeek = weekOf(newest);
            YearMonth newestMonth = YearMonth.from(newest);
            Set<LocalDate> daysSeen = new HashSet<>();
            Set<LocalDate> weeksSeen = new HashSet<>();
            Set<YearMonth> monthsSeen = new HashSet<>();

            // Newest first, so the first file met in a period is that period's newest.
            for (Candidate candidate : ordered) {
                LocalDate date = dateOf(candidate, zone);
                if (ChronoUnit.DAYS.between(date, newest) < days && daysSeen.add(date)) {
                    kept.add(candidate.name());
                }
                LocalDate week = weekOf(date);
                if (ChronoUnit.WEEKS.between(week, newestWeek) < weeks && weeksSeen.add(week)) {
                    kept.add(candidate.name());
                }
                YearMonth month = YearMonth.from(date);
                if (ChronoUnit.MONTHS.between(month, newestMonth) < months && monthsSeen.add(month)) {
                    kept.add(candidate.name());
                }
            }
            return kept;
        }

        /** The most files it can keep, when no backup answers for two periods at once. */
        public int ceiling() {
            return recent + days + weeks + months;
        }

        private static LocalDate dateOf(Candidate candidate, ZoneId zone) {
            return Instant.ofEpochMilli(candidate.modifiedMillis()).atZone(zone).toLocalDate();
        }

        private static LocalDate weekOf(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(StatementPeriod.FIRST_DAY_OF_WEEK));
        }
    }

    private static List<Candidate> newestFirst(List<Candidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingLong(Candidate::modifiedMillis).reversed())
                .toList();
    }
}
