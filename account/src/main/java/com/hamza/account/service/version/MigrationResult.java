package com.hamza.account.service.version;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MigrationResult {

    private final boolean updated;
    private final String oldVersion;
    private final String requiredVersion;
    private final List<String> executedVersions;
    private final String message;

    public static MigrationResult noUpdateRequired(String currentVersion, String requiredVersion) {
        return MigrationResult.builder()
                .updated(false)
                .oldVersion(currentVersion)
                .requiredVersion(requiredVersion)
                .executedVersions(List.of())
                .message("Database is already up to date")
                .build();
    }

    public static MigrationResult updated(
            String oldVersion,
            String requiredVersion,
            List<String> executedVersions
    ) {
        return MigrationResult.builder()
                .updated(true)
                .oldVersion(oldVersion)
                .requiredVersion(requiredVersion)
                .executedVersions(executedVersions)
                .message("Database updated successfully")
                .build();
    }
}
