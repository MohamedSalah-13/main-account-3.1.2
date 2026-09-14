package com.hamza.account.features.export;

import java.util.List;

/**
 * A report printed as a tree: each branch is a heading line spanning the whole width, the rows that
 * belong to it underneath, and a line summing them; one line summing every branch closes the table.
 * <p>
 * Every row, every branch's summary and the closing line carry one cell per heading. A short row
 * would not fail in iText - it would shift every following cell one column over, which reads as a
 * report whose figures sit under the wrong headings. The constructor refuses it instead.
 *
 * @param headers      the column headings, first logical column first (it prints on the right)
 * @param columnWidths relative widths, one per heading
 * @param branches     the groups, in the order they print
 * @param totals       the closing line, or null for none
 */
public record TreePdfLayout(String[] headers, float[] columnWidths, List<Branch> branches, String[] totals) {

    public TreePdfLayout {
        if (headers == null || headers.length == 0) {
            throw new IllegalArgumentException("A tree report needs at least one column");
        }
        if (columnWidths == null || columnWidths.length != headers.length) {
            throw new IllegalArgumentException("One width per column is required");
        }
        branches = List.copyOf(branches);
        for (Branch branch : branches) {
            for (String[] row : branch.rows()) {
                requireWidth(row, headers.length);
            }
            if (branch.summary() != null) {
                requireWidth(branch.summary(), headers.length);
            }
        }
        if (totals != null) {
            requireWidth(totals, headers.length);
        }
    }

    /**
     * @param title   the heading line, printed once across every column
     * @param rows    the branch's own lines
     * @param summary the line under them, or null for none
     */
    public record Branch(String title, List<String[]> rows, String[] summary) {
        public Branch {
            rows = List.copyOf(rows);
        }
    }

    private static void requireWidth(String[] cells, int columns) {
        if (cells == null || cells.length != columns) {
            throw new IllegalArgumentException("Expected " + columns + " cells, got "
                    + (cells == null ? "none" : cells.length));
        }
    }
}
