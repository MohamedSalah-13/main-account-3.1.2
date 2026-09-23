package com.hamza.account.features.startup;

/**
 * What the program does between being launched and showing the sign-in window, in the order it does it.
 *
 * <p>The weight is the step's share of the progress bar, and says roughly how long it can take on a slow
 * machine rather than how long it usually does: an ordinary start runs the whole list in a second or two,
 * and it is the first install - eighty migrations - or an update behind a full {@code mysqldump} that
 * someone watches. A step the start does not need (no backup, nothing to migrate) is passed over and
 * counted as done, so the bar still ends at the end.</p>
 */
public enum StartupStep {

    CONFIGURATION("startup.step.configuration", 4),
    DATABASE("startup.step.database", 10),
    BACKUP("startup.step.backup", 20),
    MIGRATION("startup.step.migration", 40),
    SETTINGS("startup.step.settings", 6),
    LICENSE("startup.step.license", 6),
    PRODUCT("startup.step.product", 4),
    SERVICES("startup.step.services", 10);

    private final String messageKey;
    private final int weight;

    StartupStep(String messageKey, int weight) {
        this.messageKey = messageKey;
        this.weight = weight;
    }

    /** The line the step writes in the startup log, and the heading while it runs. */
    public String messageKey() {
        return messageKey;
    }

    public int weight() {
        return weight;
    }

    static int totalWeight() {
        int total = 0;
        for (StartupStep step : values()) {
            total += step.weight;
        }
        return total;
    }
}
