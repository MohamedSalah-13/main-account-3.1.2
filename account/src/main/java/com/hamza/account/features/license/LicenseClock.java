package com.hamza.account.features.license;

import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Which day a licence is judged on, given that the computer's own clock is the one thing
 * its owner can move.
 *
 * <p>The latest of the sources wins: the computer, the database server, and the last day
 * this licence was seen on. Setting the computer back therefore gains nothing - and it
 * <b>costs</b> nothing either. {@link #rolledBack()} is there to be logged and shown, never
 * to be punished: a dead BIOS battery sends a shop's till back to 2009 every morning, and a
 * rule that treated that as fraud would be a rule that ends honest installs.
 *
 * @param today      the day to judge by
 * @param rolledBack true when the computer's clock is behind another source
 */
public record LicenseClock(LocalDate today, boolean rolledBack) {

    /**
     * @param computer what this computer says; never null
     * @param database what the database server says, or null when it was not asked
     * @param lastSeen the latest day this licence was judged on before, or null
     */
    public static LicenseClock of(LocalDate computer, LocalDate database, LocalDate lastSeen) {
        Objects.requireNonNull(computer, "computer");
        LocalDate latest = Stream.of(computer, database, lastSeen)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(computer);
        return new LicenseClock(latest, computer.isBefore(latest));
    }
}
