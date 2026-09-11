package com.hamza.account.features.party.trend;

import com.hamza.account.features.party.statement.StatementPeriod;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * How the trend chart groups days into periods: by year, by month, or by week.
 * <p>
 * <b>The periods are counted here, in Java, and not in SQL.</b> The query answers one row per
 * day and this enum files each day into its period. A week in MySQL is a matter of
 * {@code WEEK()} modes and {@code DATE_FORMAT} patterns - a second definition of "which week",
 * next to the one {@link StatementPeriod} already has, and one no unit test could read. So the
 * week starts on {@link StatementPeriod#FIRST_DAY_OF_WEEK} here too: a Saturday belongs to the
 * same week on the statement and on the chart.
 * <p>
 * Every function takes the day it is asked about, so {@code TrendGranularityTest} can ask about
 * a leap year, a January and a Friday without waiting for one.
 */
public enum TrendGranularity {

    YEAR("party.trend.granularity.year", 5),
    MONTH("party.trend.granularity.month", 12),
    WEEK("party.trend.granularity.week", 12);

    private final String messageKey;
    private final int defaultPeriods;

    TrendGranularity(String messageKey, int defaultPeriods) {
        this.messageKey = messageKey;
        this.defaultPeriods = defaultPeriods;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /** The first day of the period {@code day} falls in. */
    public LocalDate start(LocalDate day) {
        Objects.requireNonNull(day, "day");
        return switch (this) {
            case YEAR -> day.withDayOfYear(1);
            case MONTH -> day.withDayOfMonth(1);
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(StatementPeriod.FIRST_DAY_OF_WEEK));
        };
    }

    /** The first day of the period after the one starting on {@code start}. */
    public LocalDate next(LocalDate start) {
        return switch (this) {
            case YEAR -> start.plusYears(1);
            case MONTH -> start.plusMonths(1);
            case WEEK -> start.plusWeeks(1);
        };
    }

    /**
     * Where the chart opens for this grouping: the last five years, twelve months or twelve
     * weeks, the current one included. Always the first day of a period, so the first point is a
     * whole period rather than the tail of one.
     */
    public LocalDate defaultFrom(LocalDate today) {
        LocalDate current = start(today);
        int back = defaultPeriods - 1;
        return switch (this) {
            case YEAR -> current.minusYears(back);
            case MONTH -> current.minusMonths(back);
            case WEEK -> current.minusWeeks(back);
        };
    }

    /**
     * What the axis writes under a period. Digits only, so it reads the same in either language:
     * {@code 2026}, {@code 2026-09}, and for a week the date of its Saturday.
     */
    public String label(LocalDate start) {
        return switch (this) {
            case YEAR -> String.valueOf(start.getYear());
            case MONTH -> "%d-%02d".formatted(start.getYear(), start.getMonthValue());
            case WEEK -> start.toString();
        };
    }

    /** How many periods the chart would draw between two days, both included. */
    public int periods(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate start = start(from); !start.isAfter(to); start = next(start)) {
            count++;
        }
        return count;
    }

    /**
     * Whether a comparison with the year before says anything the chart does not already show.
     * By year it does not: last year is simply the point beside this one.
     */
    public boolean comparesWithPreviousYear() {
        return this != YEAR;
    }
}
