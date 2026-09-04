package com.hamza.account.features.itemreports;

/**
 * One column of a report: what it is called, what kind of value it holds, and how much of
 * the table's width it deserves.
 * <p>
 * The kind is what the single renderer needs in order to align, format and export a value
 * without asking the report anything else - a number right-aligned to two places, a count
 * as a whole number, text as it stands. It is deliberately a small closed set: a report
 * needing a fifth kind is a report that should be handing back a formatted string.
 * <p>
 * The weight is what stops every column from being given the same share of the table. A
 * name is long and is the column the reader is scanning for; a count is four characters
 * wide and is read once. Dividing the width equally between them is what left the reports
 * with an unreadable row of slivers and one wide column of figures.
 *
 * @param titleKey an i18n key, never a literal - every report title and column heading in
 *                 this package is translated, which is the rule the localization
 *                 architecture test already enforces on the rest of the application
 * @param weight   this column's share of the table, relative to its siblings
 */
public record ItemReportColumn(String titleKey, Kind kind, int weight) {

    public enum Kind {
        /** Names, codes, group headings. Aligned to the start of the line. */
        TEXT,
        /** A quantity or a price. Two decimal places, aligned to the end of the line. */
        NUMBER,
        /** A whole number of things. No decimal places. */
        COUNT,
        /** A date already formatted by the report, which knows whether it has one at all. */
        DATE
    }

    /**
     * The item's name, and always the widest column in any report that has one.
     * <p>
     * Not a kind of its own - it renders and exports exactly as {@link #text} does. It is
     * the one column whose content has no bound: a code is six digits and a price is eight
     * characters, but a name is however long the business made it, and truncating it is
     * what makes a row unidentifiable.
     */
    public static ItemReportColumn name(String titleKey) {
        return new ItemReportColumn(titleKey, Kind.TEXT, 6);
    }

    public static ItemReportColumn text(String titleKey) {
        return new ItemReportColumn(titleKey, Kind.TEXT, 2);
    }

    public static ItemReportColumn number(String titleKey) {
        return new ItemReportColumn(titleKey, Kind.NUMBER, 2);
    }

    public static ItemReportColumn count(String titleKey) {
        return new ItemReportColumn(titleKey, Kind.COUNT, 1);
    }

    public static ItemReportColumn date(String titleKey) {
        return new ItemReportColumn(titleKey, Kind.DATE, 2);
    }
}
