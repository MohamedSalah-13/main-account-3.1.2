package com.hamza.account.features.export;

import java.util.List;
import java.util.Objects;

/**
 * A document a customer is handed - an invoice, a return - as lines of text ready to be placed on
 * an upright page: the letterhead, what the document is, who it is for, its lines, and what it
 * comes to. {@link PdfExportService#exportDocument} draws it.
 * <p>
 * <b>It is not a report.</b> A report is a table with a title above it, and turns its page
 * sideways once it has more than a handful of columns ({@code TablePdfReport.pageSizeFor}). An
 * invoice is always upright, whatever it holds, and carries a letterhead and a summary box that a
 * report has no place for - which is why the invoice printed through the report path came out
 * sideways, without the company, without what was paid and without what was left.
 * <p>
 * Every value arrives already written - money through {@code Columns.money}, labels already
 * translated - so the renderer decides where text goes and nothing about what it says.
 *
 * @param logo       the company's picture, or null to print the name alone
 * @param identity   what the document is: its number and date, printed opposite the letterhead
 * @param details    who and where: the party, the payment type, the warehouse, and so on
 * @param totals     one cell per header, or null for no totals line
 * @param summary    what the document comes to, top to bottom
 * @param notes      free text printed under the table, or blank for none
 */
public record DocumentPdfPage(
        String title,
        String companyName,
        List<String> companyLines,
        byte[] logo,
        List<Field> identity,
        List<Field> details,
        String[] headers,
        float[] columnWidths,
        List<String[]> rows,
        String[] totals,
        List<Field> summary,
        String notesLabel,
        String notes,
        String signatureLabel,
        String footer) {

    public DocumentPdfPage {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(columnWidths, "columnWidths");
        if (headers.length != columnWidths.length) {
            throw new IllegalArgumentException("Each header needs a width: "
                    + headers.length + " headers, " + columnWidths.length + " widths");
        }
        if (totals != null && totals.length != headers.length) {
            throw new IllegalArgumentException("The totals line needs one cell per header");
        }
        for (String[] row : rows) {
            if (row.length != headers.length) {
                throw new IllegalArgumentException("Every row needs one cell per header");
            }
        }
        title = text(title);
        companyName = text(companyName);
        companyLines = List.copyOf(companyLines);
        identity = List.copyOf(identity);
        details = List.copyOf(details);
        rows = List.copyOf(rows);
        summary = List.copyOf(summary);
        notesLabel = text(notesLabel);
        notes = text(notes);
        signatureLabel = text(signatureLabel);
        footer = text(footer);
    }

    /**
     * One labelled value.
     *
     * @param emphasised printed bold on a shaded band - the figure the reader is looking for
     */
    public record Field(String label, String value, boolean emphasised) {

        public Field {
            label = text(label);
            value = text(value);
        }

        public static Field of(String label, String value) {
            return new Field(label, value, false);
        }

        public static Field emphasised(String label, String value) {
            return new Field(label, value, true);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
