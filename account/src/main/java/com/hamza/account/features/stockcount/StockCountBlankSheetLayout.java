package com.hamza.account.features.stockcount;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * The paper of {@link StockCountBlankSheet}, through {@link DocumentPdfPage} like the count record:
 * the warehouse, a line for the date and one for whoever counts, the items, and an empty column for
 * each figure. The item's name is the second column because that is the one the page writes as
 * text, aligned to its reading edge.
 */
public final class StockCountBlankSheetLayout {

    /** # | item | code | group | unit | counted - sized for an upright A4, the empty column wide enough to write in. */
    static final float[] WIDTHS = {0.45f, 3.0f, 1.3f, 1.4f, 0.8f, 1.3f};

    private StockCountBlankSheetLayout() {
    }

    /** @param printedAt when it was printed, as the footer writes it, or blank for no footer */
    public static DocumentPdfPage of(StockCountBlankSheet sheet, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        InvoicePrintDocument.Letterhead company = letterhead == null ? InvoicePrintDocument.Letterhead.EMPTY : letterhead;

        String[] headers = {"#", labels.text("item.stockcount.column.item"), labels.text("stocks.transfer.column.code"),
                labels.text("item.stockcount.blank.column.group"), labels.text("item.column.unit"),
                labels.text("item.stockcount.column.counted")};
        List<String[]> rows = new ArrayList<>();
        int number = 1;
        for (StockCountBlankSheet.Row row : sheet.rows()) {
            rows.add(new String[]{String.valueOf(number++), value(row.name()), value(row.code()),
                    value(row.group()), value(row.unit()), ""});
        }

        return new DocumentPdfPage(
                labels.text("item.stockcount.blank.title"),
                company.name(),
                letterheadLines(company, labels),
                company.logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("invoice.stock"), value(sheet.stockName())),
                        DocumentPdfPage.Field.of(labels.text("item.stockcount.label.count.date"), "")),
                List.of(DocumentPdfPage.Field.of(labels.text("item.stockcount.blank.counted.by"), "")),
                headers,
                WIDTHS.clone(),
                rows,
                null,
                List.of(DocumentPdfPage.Field.of(labels.text("item.stockcount.blank.item.count"),
                        String.valueOf(sheet.rows().size()))),
                labels.text("invoice.notes"),
                "",
                labels.text("item.stockcount.sheet.signature"),
                printedAt == null || printedAt.isBlank() ? ""
                        : labels.text("invoice.pdf.printed.at") + ": " + printedAt);
    }

    private static List<String> letterheadLines(InvoicePrintDocument.Letterhead letterhead,
                                                InvoicePdfLayout.Labels labels) {
        List<String> lines = new ArrayList<>();
        if (!letterhead.address().isBlank()) {
            lines.add(letterhead.address());
        }
        if (!letterhead.phone().isBlank()) {
            lines.add(labels.text("invoice.pdf.phone") + ": " + letterhead.phone());
        }
        return lines;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
