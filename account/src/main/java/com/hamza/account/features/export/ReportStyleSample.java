package com.hamza.account.features.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;

/**
 * The pages the settings screen previews a {@link ReportStyle} on: a report long enough to reach the
 * foot of its first page, and an invoice carrying the shop's own letterhead.
 * <p>
 * Made up, and says so in its title, but shaped like the real thing - the columns an item report
 * prints, a totals line that is the sum of the rows above it - so a size or a colour is judged on the
 * page it will be used on.
 */
public final class ReportStyleSample {

    /** Enough rows that the first page is full and its number is at its foot. */
    static final int REPORT_ROWS = 45;
    static final int DOCUMENT_LINES = 6;

    /** Resolves a message key. Named {@code text} so the message-key architecture test sees the keys. */
    @FunctionalInterface
    public interface Labels {
        String text(String key);
    }

    /** A flat report as {@link PdfExportService#exportGroupedReport} takes it. */
    public record SampleReport(String title, String subtitle, String[] headers, float[] columnWidths,
                               List<String[]> rows, String[] totals) {
    }

    private ReportStyleSample() {
    }

    public static SampleReport report(Labels labels, LocalDate today) {
        String[] headers = {
                labels.text("invoice.pdf.column.row"),
                labels.text("invoice.pdf.column.item"),
                labels.text("invoice.pdf.column.quantity"),
                labels.text("invoice.pdf.column.price"),
                labels.text("invoice.pdf.column.total")};
        String itemPattern = labels.text("report.style.sample.item");
        List<String[]> rows = new ArrayList<>();
        int quantityAll = 0;
        BigDecimal totalAll = BigDecimal.ZERO;
        for (int i = 1; i <= REPORT_ROWS; i++) {
            int quantity = quantity(i);
            BigDecimal price = price(i);
            BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
            quantityAll += quantity;
            totalAll = totalAll.add(total);
            rows.add(new String[]{String.valueOf(i), item(itemPattern, i), String.valueOf(quantity),
                    money(price), money(total)});
        }
        String[] totals = {"", labels.text("total"), String.valueOf(quantityAll), "", money(totalAll)};
        String subtitle = format(labels.text("report.style.sample.subtitle"), today.withDayOfMonth(1), today);
        return new SampleReport(labels.text("report.style.sample.report.title"), subtitle, headers,
                new float[]{0.6f, 3.2f, 1f, 1.3f, 1.5f}, rows, totals);
    }

    /**
     * An invoice with the shop's letterhead - the one the settings screen read - so that leaving it off
     * is seen for what it is.
     */
    public static DocumentPdfPage document(Labels labels, ReportLetterhead letterhead, LocalDate today,
                                           String printedAt) {
        String[] headers = {
                labels.text("invoice.pdf.column.row"),
                labels.text("invoice.pdf.column.item"),
                labels.text("invoice.pdf.column.barcode"),
                labels.text("invoice.pdf.column.unit"),
                labels.text("invoice.pdf.column.quantity"),
                labels.text("invoice.pdf.column.price"),
                labels.text("invoice.pdf.column.discount"),
                labels.text("invoice.pdf.column.total")};
        String itemPattern = labels.text("report.style.sample.item");
        String unit = labels.text("report.style.sample.unit");
        List<String[]> rows = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        for (int i = 1; i <= DOCUMENT_LINES; i++) {
            int quantity = quantity(i);
            BigDecimal price = price(i);
            BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
            net = net.add(total);
            rows.add(new String[]{String.valueOf(i), item(itemPattern, i), "622100000" + (1000 + i), unit,
                    String.valueOf(quantity), money(price), money(BigDecimal.ZERO), money(total)});
        }
        String[] totals = {"", labels.text("total"), "", "", "", "", money(BigDecimal.ZERO), money(net)};
        ReportLetterhead company = letterhead == null ? ReportLetterhead.EMPTY : letterhead;
        return new DocumentPdfPage(
                labels.text("report.style.sample.document.title"),
                company.name(), company.lines(), company.logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("invoice.pdf.number"), "1024"),
                        DocumentPdfPage.Field.of(labels.text("invoice.pdf.date"), today.toString())),
                List.of(DocumentPdfPage.Field.of(labels.text("invoice.pdf.party.customer"),
                                labels.text("report.style.sample.customer")),
                        DocumentPdfPage.Field.of(labels.text("invoice.pdf.payment.type"), labels.text("cash"))),
                headers, new float[]{0.5f, 3.2f, 1.7f, 1.0f, 1.0f, 1.2f, 1.0f, 1.4f}, rows, totals,
                List.of(DocumentPdfPage.Field.of(labels.text("total"), money(net)),
                        DocumentPdfPage.Field.emphasised(labels.text("report.style.sample.net"), money(net))),
                labels.text("invoice.pdf.notes"), "",
                labels.text("invoice.pdf.signature"),
                printedAt == null || printedAt.isBlank() ? ""
                        : labels.text("invoice.pdf.printed.at") + ": " + printedAt);
    }

    private static int quantity(int row) {
        return row % 5 + 1;
    }

    private static BigDecimal price(int row) {
        return BigDecimal.valueOf(1250 + row * 375L, 2);
    }

    private static String item(String pattern, int row) {
        return pattern.contains("%d") ? format(pattern, row) : pattern + " " + row;
    }

    /** A translation with a broken placeholder previews as its own text rather than failing the preview. */
    private static String format(String pattern, Object... values) {
        try {
            return String.format(pattern, values);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }
}
