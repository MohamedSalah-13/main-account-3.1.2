package com.hamza.account.features.capital;

import java.time.LocalDate;

/**
 * The period an equity statement covers. The checks are in the constructor, and {@link #problem}
 * is how a screen asks first and says so in words.
 */
public record CapitalFilter(LocalDate from, LocalDate to) {

    public enum Problem {
        NONE,
        MISSING,
        REVERSED
    }

    public CapitalFilter {
        Problem problem = problem(from, to);
        if (problem != Problem.NONE) {
            throw new IllegalArgumentException("cannot report capital from " + from + " to " + to + ": " + problem);
        }
    }

    public static Problem problem(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Problem.MISSING;
        }
        return from.isAfter(to) ? Problem.REVERSED : Problem.NONE;
    }

    /** Where the screen opens: the year so far, which is how an owner reads his capital. */
    public static CapitalFilter yearToDate(LocalDate today) {
        return new CapitalFilter(today.withDayOfYear(1), today);
    }
}
