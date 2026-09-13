package com.hamza.account.features.export;

/** The user's chosen destination for reports produced as PDF. */
public enum ReportOutputMode {
    SAVE_PDF,
    PRINT_DIRECT,
    ASK;

    public static ReportOutputMode fromStoredValue(String value) {
        try {
            return value == null ? SAVE_PDF : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SAVE_PDF;
        }
    }
}
