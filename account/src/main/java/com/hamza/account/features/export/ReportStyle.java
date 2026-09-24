package com.hamza.account.features.export;

import java.util.Objects;

/**
 * How a printed report looks: its type sizes, what its head and foot carry, whether its pages are
 * numbered, its colours, and on an invoice or a voucher whether the company's letterhead is printed.
 * <p>
 * <b>It is the shop's, not a computer's.</b> A statement handed to a customer from one till and from
 * another must look the same, so the settings screen stores it under one key of
 * {@code SharedSettingKeys}. The paper size and the printer stay the computer's.
 * <p>
 * <b>{@link #DEFAULT} prints what was printed before there was a choice</b> - the same sizes, the same
 * blue, the same head and foot - with one addition: a report's pages are numbered the way an invoice's
 * always were ({@code 1 / 3}). Nobody's paper changes on upgrade beyond that.
 * <p>
 * Every value is held inside its range by the constructor, so a value stored by a later build, or typed
 * into the preferences by hand, prints a readable page rather than failing the file.
 *
 * @param titleSize              the report's name
 * @param subtitleSize           the lines under it - the period, the filters
 * @param headerSize             the table's column headings
 * @param bodySize               the table's rows; a line under a branch heading is a point smaller, as it
 *                               always was
 * @param totalsSize             a subtotal and the closing totals line
 * @param smallSize              the date it was printed, who printed it, the footer; a page number is two
 *                               points smaller
 * @param compactRows            rows as tall as their text; the bundled Naskh face declares a line far
 *                               taller than its letters, so by default a row of eleven points stands
 *                               almost three times that
 * @param showLetterhead         the company's name, address and logo above a report; off by default,
 *                               since no report carried them before
 * @param showPrintedBy          the name of whoever printed it; off by default - a statement is handed to
 *                               a customer
 * @param footerText             the closing sentence, or blank for the application's own
 * @param inkSaver               no filled heading, band or striping: rules and bold type instead, for a
 *                               monochrome laser printer that turns a blue heading into a grey block
 * @param showDocumentLetterhead the letterhead on an invoice, a voucher or a slip; off for paper that
 *                               already has the company printed on it
 * @param documentTopSpaceMm     space left empty above such a document when its letterhead is off, for
 *                               that printed heading to sit in
 */
public record ReportStyle(
        int titleSize,
        int subtitleSize,
        int headerSize,
        int bodySize,
        int totalsSize,
        int smallSize,
        boolean compactRows,
        PageNumbering pageNumbering,
        boolean showLetterhead,
        boolean showTitle,
        boolean showSubtitle,
        boolean showPrintedAt,
        boolean showPrintedBy,
        boolean showFooter,
        String footerText,
        ReportPalette palette,
        boolean inkSaver,
        boolean showDocumentLetterhead,
        int documentTopSpaceMm) {

    public static final int MIN_FONT_SIZE = 6;
    public static final int MAX_FONT_SIZE = 40;
    public static final int MAX_FOOTER_LENGTH = 150;
    public static final int MAX_TOP_SPACE_MM = 80;

    public static final ReportStyle DEFAULT = new ReportStyle(
            20, 12, 11, 11, 10, 10, false,
            PageNumbering.SLASH,
            false, true, true, true, false, true, "",
            ReportPalette.BLUE, false,
            true, 0);

    public ReportStyle {
        titleSize = fontSize(titleSize);
        subtitleSize = fontSize(subtitleSize);
        headerSize = fontSize(headerSize);
        bodySize = fontSize(bodySize);
        totalsSize = fontSize(totalsSize);
        smallSize = fontSize(smallSize);
        pageNumbering = Objects.requireNonNullElse(pageNumbering, PageNumbering.SLASH);
        footerText = footer(footerText);
        palette = Objects.requireNonNullElse(palette, ReportPalette.BLUE);
        documentTopSpaceMm = Math.clamp(documentTopSpaceMm, 0, MAX_TOP_SPACE_MM);
    }

    /** A size inside {@link #MIN_FONT_SIZE}..{@link #MAX_FONT_SIZE}. */
    public static int fontSize(int size) {
        return Math.clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE);
    }

    /**
     * One line, trimmed, at most {@link #MAX_FOOTER_LENGTH} characters. A line break would be written as
     * one paragraph shaped before iText wraps it - the defect that prints a wrapped Arabic line end first.
     */
    private static String footer(String text) {
        if (text == null) {
            return "";
        }
        String line = text.replaceAll("[\\r\\n\\t]+", " ").strip();
        return line.length() > MAX_FOOTER_LENGTH ? line.substring(0, MAX_FOOTER_LENGTH).strip() : line;
    }

    /** A line under a branch heading or in a statement: a point smaller than a flat table's row. */
    public int branchRowSize() {
        return fontSize(bodySize - 1);
    }

    /** A page number: two points smaller than the footer it sits under. */
    public int pageNumberSize() {
        return fontSize(smallSize - 2);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A copy with some values changed - the settings screen and the tests build one this way. */
    public static final class Builder {
        private int titleSize;
        private int subtitleSize;
        private int headerSize;
        private int bodySize;
        private int totalsSize;
        private int smallSize;
        private boolean compactRows;
        private PageNumbering pageNumbering;
        private boolean showLetterhead;
        private boolean showTitle;
        private boolean showSubtitle;
        private boolean showPrintedAt;
        private boolean showPrintedBy;
        private boolean showFooter;
        private String footerText;
        private ReportPalette palette;
        private boolean inkSaver;
        private boolean showDocumentLetterhead;
        private int documentTopSpaceMm;

        private Builder(ReportStyle from) {
            titleSize = from.titleSize;
            subtitleSize = from.subtitleSize;
            headerSize = from.headerSize;
            bodySize = from.bodySize;
            totalsSize = from.totalsSize;
            smallSize = from.smallSize;
            compactRows = from.compactRows;
            pageNumbering = from.pageNumbering;
            showLetterhead = from.showLetterhead;
            showTitle = from.showTitle;
            showSubtitle = from.showSubtitle;
            showPrintedAt = from.showPrintedAt;
            showPrintedBy = from.showPrintedBy;
            showFooter = from.showFooter;
            footerText = from.footerText;
            palette = from.palette;
            inkSaver = from.inkSaver;
            showDocumentLetterhead = from.showDocumentLetterhead;
            documentTopSpaceMm = from.documentTopSpaceMm;
        }

        public Builder titleSize(int value) {
            titleSize = value;
            return this;
        }

        public Builder subtitleSize(int value) {
            subtitleSize = value;
            return this;
        }

        public Builder headerSize(int value) {
            headerSize = value;
            return this;
        }

        public Builder bodySize(int value) {
            bodySize = value;
            return this;
        }

        public Builder totalsSize(int value) {
            totalsSize = value;
            return this;
        }

        public Builder smallSize(int value) {
            smallSize = value;
            return this;
        }

        public Builder compactRows(boolean value) {
            compactRows = value;
            return this;
        }

        public Builder pageNumbering(PageNumbering value) {
            pageNumbering = value;
            return this;
        }

        public Builder showLetterhead(boolean value) {
            showLetterhead = value;
            return this;
        }

        public Builder showTitle(boolean value) {
            showTitle = value;
            return this;
        }

        public Builder showSubtitle(boolean value) {
            showSubtitle = value;
            return this;
        }

        public Builder showPrintedAt(boolean value) {
            showPrintedAt = value;
            return this;
        }

        public Builder showPrintedBy(boolean value) {
            showPrintedBy = value;
            return this;
        }

        public Builder showFooter(boolean value) {
            showFooter = value;
            return this;
        }

        public Builder footerText(String value) {
            footerText = value;
            return this;
        }

        public Builder palette(ReportPalette value) {
            palette = value;
            return this;
        }

        public Builder inkSaver(boolean value) {
            inkSaver = value;
            return this;
        }

        public Builder showDocumentLetterhead(boolean value) {
            showDocumentLetterhead = value;
            return this;
        }

        public Builder documentTopSpaceMm(int value) {
            documentTopSpaceMm = value;
            return this;
        }

        public ReportStyle build() {
            return new ReportStyle(titleSize, subtitleSize, headerSize, bodySize, totalsSize, smallSize,
                    compactRows, pageNumbering, showLetterhead, showTitle, showSubtitle, showPrintedAt,
                    showPrintedBy, showFooter, footerText, palette, inkSaver, showDocumentLetterhead,
                    documentTopSpaceMm);
        }
    }
}
