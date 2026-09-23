package com.hamza.account.features.startup;

import java.util.List;

/**
 * The startup as it stands, handed to the window after every change. Immutable, so the thread doing the
 * work and the thread drawing it never share a list.
 *
 * @param current  the step running, or the one that failed; null before the first and after the last
 * @param progress 0 to 1
 */
public record StartupSnapshot(List<StartupLine> lines, StartupStep current, double progress, Outcome outcome) {

    public enum Outcome { RUNNING, READY, FAILED }

    public StartupSnapshot {
        lines = List.copyOf(lines);
    }
}
