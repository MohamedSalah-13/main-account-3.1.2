package com.hamza.account.features.itemreports;

import java.util.Arrays;
import java.util.List;

/**
 * One line of a report.
 * <p>
 * A flat list with a depth on each row, rather than a tree of nodes, and that is a
 * deliberate choice: every report in this package - including the group breakdown, which
 * is genuinely three levels deep - renders in the same table, prints through the same
 * exporter and pages the same way. A tree model would need its own control, its own
 * export and its own printing, for the sake of one report out of four.
 *
 * @param depth  0 for a top-level row, 1 for a row under it, and so on. The first column
 *               is indented by it; nothing else in the application reads it.
 * @param kind   what the row is for. A total is not an item, and counting rows without
 *               asking would report a group's heading as one of its products.
 * @param itemId the item this row is about, or {@code null} for a heading or a total. It
 *               is what lets the screen open the item card from a report row.
 * @param values one per column, in the column order the report declared
 */
public record ItemReportRow(int depth, Kind kind, Integer itemId, List<Object> values) {

    public enum Kind {
        /** A product. */
        ITEM,
        /** A heading that groups the rows under it. */
        GROUP,
        /** A figure summarising the rows above it at the same depth. */
        TOTAL
    }

    /**
     * Deliberately the only factory for an item row, with the depth always spelled out.
     * A convenience overload taking the id alone would be ambiguous against this one - a
     * call passing two ints would compile either way and mean different things - and the
     * compiler resolving it silently is worse than typing the zero.
     */
    public static ItemReportRow item(int depth, int itemId, Object... values) {
        return new ItemReportRow(depth, Kind.ITEM, itemId, Arrays.asList(values));
    }

    public static ItemReportRow group(int depth, Object... values) {
        return new ItemReportRow(depth, Kind.GROUP, null, Arrays.asList(values));
    }

    public static ItemReportRow total(int depth, Object... values) {
        return new ItemReportRow(depth, Kind.TOTAL, null, Arrays.asList(values));
    }

    /** The value in one column, or {@code null} where this row does not fill that column. */
    public Object value(int column) {
        return column >= 0 && column < values.size() ? values.get(column) : null;
    }
}
