package com.hamza.account.controller.items;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.itemreports.ItemReportColumn;
import com.hamza.account.features.itemreports.ItemReportResult;
import com.hamza.account.features.itemreports.ItemReportRow;
import com.itextpdf.kernel.geom.PageSize;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints any item report to PDF.
 * <p>
 * <b>One adapter for every report there is, rather than a Jasper template each.</b> That
 * is the same choice the rest of this package makes: a report declares its columns and
 * hands back rows, so the thing that draws them is written once. The application's other
 * printing is JasperReports and stays that way - an invoice, an 80mm receipt and a sheet of
 * barcode labels are fixed layouts where a template is exactly the right tool, and their
 * thirty-two {@code .jrxml} files are worth what they cost. A tabular report is not that
 * shape: a template per report would mean hand-authoring a thirty-third file, with its own
 * Arabic font extension and its own right-to-left bands, every time somebody adds a class
 * to {@code ItemReportCatalog}.
 * <p>
 * {@link PdfExportService} already solves the hard half - an embedded Arabic font,
 * right-to-left base direction and cell order - and its {@code exportGenericReport} takes
 * headers, column widths and rows, which is precisely the shape of an
 * {@link ItemReportResult}. The column weights the screen sizes its table by are the same
 * numbers the PDF proportions its columns by, so the printed report looks like the one on
 * screen for the same reason rather than by coincidence.
 */
public final class ItemReportPdf {

    private ItemReportPdf() {
    }

    /**
     * Writes the report to {@code filePath}.
     *
     * @param subtitle what was asked - the filter, the date - so a printed page still says
     *                 what question it answers once it is off the screen
     * @return whether the file was written
     */
    public static boolean write(ItemReportResult result, String title, String subtitle, String filePath) {
        LanguageManager language = LanguageManager.getInstance();

        List<ItemReportColumn> columns = result.columns();
        String[] headers = columns.stream()
                .map(column -> language.getString(column.titleKey()))
                .toArray(String[]::new);
        float[] widths = new float[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            widths[index] = columns.get(index).weight();
        }

        List<String[]> rows = new ArrayList<>();
        for (ItemReportRow row : result.rows()) {
            rows.add(render(row, columns));
        }

        // Landscape: these reports are eight to ten columns wide, and portrait squeezes the
        // name column - the one the reader is scanning - into a stack of single letters.
        return new PdfExportService().exportGenericReport(
                filePath, title, subtitle, headers, widths, rows,
                totalsLabel(result), totalsValue(result), null, PageSize.A4.rotate());
    }

    /**
     * One row as text.
     * <p>
     * The depth is spent on indenting the first column, exactly as the table does it, so a
     * three-level report prints as a report rather than as a flat list in which a group
     * heading is indistinguishable from the products under it.
     */
    private static String[] render(ItemReportRow row, List<ItemReportColumn> columns) {
        String[] cells = new String[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            Object value = row.value(index);
            String text = value == null ? "" : format(value, columns.get(index).kind());
            cells[index] = index == 0 ? "    ".repeat(row.depth()) + text : text;
        }
        return cells;
    }

    private static String format(Object value, ItemReportColumn.Kind kind) {
        if (!(value instanceof Number number)) return String.valueOf(value);
        return switch (kind) {
            case COUNT -> String.format("%,d", number.longValue());
            case NUMBER -> String.format("%,.2f", number.doubleValue());
            default -> String.valueOf(value);
        };
    }

    /**
     * The totals strip, folded into the single label and value the generic exporter offers.
     * <p>
     * A report with several totals gets them joined into one line rather than dropped: the
     * figures under the table are usually the reason the page was printed, and losing all
     * but the first would be the quiet kind of wrong.
     */
    private static String totalsLabel(ItemReportResult result) {
        if (result.totals().isEmpty()) return null;
        LanguageManager language = LanguageManager.getInstance();
        return result.totals().stream()
                .map(total -> language.getString(total.labelKey()))
                .reduce((first, second) -> first + "  |  " + second)
                .orElse(null);
    }

    private static String totalsValue(ItemReportResult result) {
        if (result.totals().isEmpty()) return null;
        return result.totals().stream()
                .map(ItemReportResult.Total::value)
                .reduce((first, second) -> first + "  |  " + second)
                .orElse(null);
    }
}
