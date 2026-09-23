package com.hamza.account.features.startup;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One line of the startup log: a message key and its arguments, never a sentence - the window resolves it
 * in the program's language, and a line written before the language is known still reads right.
 *
 * @param detail   a line under a step (one migration of many) rather than a step of its own
 * @param finished when it stopped running, or null while it runs
 */
public record StartupLine(String messageKey, List<String> arguments, State state, boolean detail,
                          Instant started, Instant finished) {

    public enum State { RUNNING, DONE, FAILED }

    public StartupLine {
        arguments = List.copyOf(arguments);
    }

    StartupLine ended(State end, Instant at) {
        return new StartupLine(messageKey, arguments, end, detail, started, at);
    }

    /** How long it has run: to its end, or to {@code now} while it still runs. */
    public Duration elapsed(Instant now) {
        return Duration.between(started, finished == null ? now : finished);
    }
}
