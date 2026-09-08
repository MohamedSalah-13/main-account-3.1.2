package com.hamza.account.features.dbsetup;

/** Whether authentication reached MySQL and whether the configured schema already exists. */
public record DatabaseProbeResult(boolean databaseExists) {
}
