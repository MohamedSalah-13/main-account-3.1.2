package com.hamza.account.features.export;

/** The user's chosen destination for reports produced as PDF. */
public enum ReportOutputMode {
    SAVE_PDF,
    PRINT_DIRECT,
    ASK,
    /**
     * The pages shown in a window first, printed or saved from there. Added last: the stored value is
     * the name, and the settings screen lists the modes in this order.
     */
    PREVIEW;

    public static ReportOutputMode fromStoredValue(String value) {
        try {
            return value == null ? SAVE_PDF : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SAVE_PDF;
        }
    }

    /** Written to a temporary file that goes once it has been printed or the window has closed. */
    public boolean usesTemporaryFile() {
        return this == PRINT_DIRECT || this == PREVIEW;
    }
}
