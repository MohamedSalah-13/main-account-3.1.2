package com.hamza.account.features.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a printed page needs beyond its own content: the shop's {@link ReportStyle}, the company
 * it is printed for, the words its head and foot are written with, and who is printing it.
 * <p>
 * The decisions about what the head and the foot say are made here, over plain values, so they are
 * tested without writing a PDF; {@link PdfExportService} places what these methods answer.
 *
 * @param userName the signed-in user's name, or blank
 */
public record ReportSetup(ReportStyle style, ReportLetterhead letterhead, ReportLabels labels, String userName) {

    /** Between the date and the name on the line under a report's title. */
    static final String SEPARATOR = "      ";

    public ReportSetup {
        style = Objects.requireNonNullElse(style, ReportStyle.DEFAULT);
        letterhead = Objects.requireNonNullElse(letterhead, ReportLetterhead.EMPTY);
        labels = Objects.requireNonNullElse(labels, ReportLabels.NONE);
        userName = userName == null ? "" : userName.strip();
    }

    /** The default style, no company, no words and nobody: what a page printed outside the program gets. */
    public static ReportSetup plain() {
        return new ReportSetup(ReportStyle.DEFAULT, ReportLetterhead.EMPTY, ReportLabels.NONE, "");
    }

    /** The same setup in another style - the settings screen previews each change this way. */
    public ReportSetup withStyle(ReportStyle newStyle) {
        return new ReportSetup(newStyle, letterhead, labels, userName);
    }

    /** Whether a report's head carries the company: the shop asked for it and there is something to print. */
    public boolean printsLetterhead() {
        return style.showLetterhead() && !letterhead.isEmpty();
    }

    /**
     * The line under a report's title: when it was printed and by whom, as the style asks, or an empty
     * string for no line at all.
     *
     * @param printedAt the date and time, already written
     */
    public String printedLine(String printedAt) {
        List<String> parts = new ArrayList<>(2);
        if (style.showPrintedAt() && printedAt != null && !printedAt.isBlank()) {
            parts.add(captioned(labels.printedAt(), printedAt.strip()));
        }
        if (style.showPrintedBy() && !userName.isEmpty()) {
            parts.add(captioned(labels.printedBy(), userName));
        }
        return String.join(SEPARATOR, parts);
    }

    /** The sentence closing a report, or an empty string for none. */
    public String footer() {
        if (!style.showFooter()) {
            return "";
        }
        return style.footerText().isEmpty() ? labels.defaultFooter() : style.footerText();
    }

    /** What one page's foot says about its number, or an empty string for none. */
    public String pageText(int page, int pages) {
        return style.pageNumbering().text(page, pages, labels.pageOf());
    }

    private static String captioned(String caption, String value) {
        return caption.isEmpty() ? value : caption + ": " + value;
    }
}
