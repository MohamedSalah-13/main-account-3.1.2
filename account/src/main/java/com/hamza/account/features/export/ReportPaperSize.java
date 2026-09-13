package com.hamza.account.features.export;

/** Page sizes deliberately supported by the configurable PDF reporting path. */
public enum ReportPaperSize {
    A4,
    A5;

    public static ReportPaperSize fromStoredValue(String value) {
        try {
            return value == null ? A4 : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return A4;
        }
    }
}
