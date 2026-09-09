package com.hamza.account.features.totals;

import com.hamza.account.document.DocumentTableSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns a report into the columns a page is made of.
 *
 * <p>Separate from {@link TotalsReportService} because the shape of a printed table is a
 * decision, not a query: which columns a report carries depends on what it groups by and
 * on whether the family earns a profit at all, and getting that wrong prints a column of
 * zeros headed "الربح" on a list of purchases. It is a pure function of the report, so it
 * is checked without a database or a toolkit.</p>
 *
 * @param headers      the column titles, right to left as the reader reads them
 * @param columnWidths their relative widths, one per header
 * @param rows         the body, already formatted
 * @param totals       the last line, one cell per header - a summary whose total line
 *                     carries a single figure leaves the reader adding up a column by
 *                     hand, which is the work the report was supposed to have done
 */
public record TotalsReportLayout(String[] headers, float[] columnWidths, List<String[]> rows,
                                 String[] totals) {

    public static TotalsReportLayout of(TotalsReportService.TotalsReport report,
                                        DocumentTableSpec.Report kind,
                                        Function<String, String> translate) {
        return report.perItem()
                ? perItem(report, kind, translate)
                : perDocument(report, kind, translate);
    }

    /** Money columns: what was billed, discounted, collected, still owed, and earned. */
    private static TotalsReportLayout perDocument(TotalsReportService.TotalsReport report,
                                                  DocumentTableSpec.Report kind,
                                                  Function<String, String> translate) {
        List<String> headers = new ArrayList<>(List.of(
                translate.apply(groupHeaderKey(kind)),
                translate.apply("count"),
                translate.apply("total"),
                translate.apply("discount"),
                translate.apply("invoice.total.after.discount"),
                translate.apply("paid"),
                translate.apply("invoice.summary.remaining")));
        if (report.hasProfit()) headers.add(translate.apply("common.profit"));

        List<String[]> rows = new ArrayList<>();
        for (TotalsReportRow row : report.rows()) {
            List<String> cells = new ArrayList<>(List.of(
                    text(row.label()), String.valueOf(row.count()), money(row.total()),
                    money(row.discount()), money(row.afterDiscount()), money(row.paid()),
                    money(row.remaining())));
            if (report.hasProfit()) cells.add(money(row.profit()));
            rows.add(cells.toArray(String[]::new));
        }
        TotalsReportRow total = report.total();
        List<String> totals = new ArrayList<>(List.of(
                translate.apply("total"), String.valueOf(total.count()), money(total.total()),
                money(total.discount()), money(total.afterDiscount()), money(total.paid()),
                money(total.remaining())));
        if (report.hasProfit()) totals.add(money(total.profit()));
        return new TotalsReportLayout(headers.toArray(String[]::new), widths(headers.size()), rows,
                totals.toArray(String[]::new));
    }

    /**
     * Quantity and revenue only. A line's share of an invoice-level discount has no owner,
     * so this report does not claim a profit per item - the same reason
     * {@code card_item_view_details} stays gross of it.
     */
    private static TotalsReportLayout perItem(TotalsReportService.TotalsReport report,
                                              DocumentTableSpec.Report kind,
                                              Function<String, String> translate) {
        String[] headers = {
                translate.apply(groupHeaderKey(kind)),
                translate.apply("invoice.report.column.invoices"),
                translate.apply("quantity"),
                translate.apply("total")};
        List<String[]> rows = new ArrayList<>();
        for (TotalsReportRow row : report.rows()) {
            rows.add(new String[]{text(row.label()), String.valueOf(row.count()),
                    quantity(row.quantity()), money(row.total())});
        }
        return new TotalsReportLayout(headers, widths(headers.length), rows, new String[]{
                translate.apply("total"), String.valueOf(report.total().count()),
                quantity(report.total().quantity()), money(report.total().total())});
    }

    private static String groupHeaderKey(DocumentTableSpec.Report kind) {
        return switch (kind) {
            case BY_PARTY -> "name";
            case BY_DAY -> "invoice.report.column.day";
            case BY_MONTH -> "invoice.report.column.month";
            case BY_DELEGATE -> "NAME_DELEGATE";
            case BY_ITEM -> "invoice.report.column.item";
        };
    }

    /** The label column takes the room; the figures are all the same width. */
    private static float[] widths(int columns) {
        float[] widths = new float[columns];
        widths[0] = 3f;
        for (int i = 1; i < columns; i++) widths[i] = 1.4f;
        return widths;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Quantities are stored to three places; trailing zeros on a count of pieces are noise. */
    private static String quantity(BigDecimal value) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
        return scaled.stripTrailingZeros().scale() <= 0
                ? scaled.stripTrailingZeros().toPlainString()
                : scaled.stripTrailingZeros().toPlainString();
    }
}
