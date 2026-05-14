package com.hamza.account.service.version;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseMigration implements Comparable<DatabaseMigration> {

    private final String version;
    private final String description;
    private final String resourcePath;

    @Override
    public int compareTo(DatabaseMigration other) {
        return compareVersions(this.version, other.version);
    }

    public static int compareVersions(String first, String second) {
        String[] firstParts = first.split("\\.");
        String[] secondParts = second.split("\\.");

        int maxLength = Math.max(firstParts.length, secondParts.length);

        for (int index = 0; index < maxLength; index++) {
            int firstValue = index < firstParts.length ? parseNumber(firstParts[index]) : 0;
            int secondValue = index < secondParts.length ? parseNumber(secondParts[index]) : 0;

            if (firstValue != secondValue) {
                return Integer.compare(firstValue, secondValue);
            }
        }

        return 0;
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
