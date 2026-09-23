package com.hamza.account.features.export;

import java.util.List;
import java.util.Objects;

/**
 * A statement printed as it is read down the page: a section's heading across the whole width, its lines
 * under it, the line it adds up to ruled off, and a result on the band a totals line wears.
 * <p>
 * Every line but a heading carries one cell per column; a heading carries its title alone. A short line
 * would not fail in iText - it would shift every following cell one column over - so the constructor
 * refuses it, as {@link TreePdfLayout}'s does.
 *
 * @param headers      the column headings, first logical column first (it prints on the right)
 * @param columnWidths relative widths, one per heading
 * @param lines        the statement, top to bottom
 */
public record StatementPdfLayout(String[] headers, float[] columnWidths, List<Line> lines) {

    public enum Style {
        HEADING, ROW, SUBTOTAL, RESULT
    }

    /** @param cells the line's cells; a heading's single cell is its title */
    public record Line(Style style, String[] cells) {
        public Line {
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(cells, "cells");
        }
    }

    public StatementPdfLayout {
        if (headers == null || headers.length == 0) {
            throw new IllegalArgumentException("A statement needs at least one column");
        }
        if (columnWidths == null || columnWidths.length != headers.length) {
            throw new IllegalArgumentException("One width per column is required");
        }
        lines = List.copyOf(lines);
        for (Line line : lines) {
            int expected = line.style() == Style.HEADING ? 1 : headers.length;
            if (line.cells().length != expected) {
                throw new IllegalArgumentException("Expected " + expected + " cells on a " + line.style()
                        + " line, got " + line.cells().length);
            }
        }
    }
}
