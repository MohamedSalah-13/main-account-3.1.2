package com.hamza.account.features.export;

/**
 * The words a report's head and foot are written with, already in the reader's language.
 * <p>
 * They come in from the edge rather than being looked up here: the renderer writes what it is handed,
 * so it holds no sentence of its own - it used to hold two Arabic ones, and printed them under an
 * English report.
 *
 * @param printedAt     the caption before the date it was printed ("تاريخ التقرير")
 * @param printedBy     the caption before who printed it
 * @param pageOf        a {@code String.format} pattern taking the page and the count ("صفحة %d من %d")
 * @param defaultFooter the closing sentence when the shop has not written its own
 */
public record ReportLabels(String printedAt, String printedBy, String pageOf, String defaultFooter) {

    /** No words at all: the date alone, the name alone, {@code 1 / 3}, and no closing sentence. */
    public static final ReportLabels NONE = new ReportLabels("", "", "", "");

    public ReportLabels {
        printedAt = text(printedAt);
        printedBy = text(printedBy);
        pageOf = text(pageOf);
        defaultFooter = text(defaultFooter);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
