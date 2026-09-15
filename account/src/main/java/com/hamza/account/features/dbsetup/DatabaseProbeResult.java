package com.hamza.account.features.dbsetup;

import java.util.Objects;

/**
 * Whether authentication reached MySQL, whether the configured schema already exists,
 * and whether the server will let this account create the triggers the migrations need.
 */
public record DatabaseProbeResult(boolean databaseExists, StoredProgramLogging storedPrograms) {

    public DatabaseProbeResult {
        Objects.requireNonNull(storedPrograms);
    }

    public DatabaseProbeResult(boolean databaseExists) {
        this(databaseExists, StoredProgramLogging.NOT_REQUIRED);
    }
}
