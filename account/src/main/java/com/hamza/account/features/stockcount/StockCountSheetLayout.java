package com.hamza.account.features.stockcount;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The paper of one stock count - محضر جرد: what was counted, what the book said, and what the post
 * moved. The one document that corrects a balance was the one document with no paper.
 * <p>
 * Built on the transfer slip through {@link DocumentPdfPage}, upright. Its columns are the screen's:
 * the book in the item's base unit, the count in the unit it was counted in, and the difference in
 * base units - which is how the screen writes them, and how a difference is posted. A draft prints
 * with its status on it, so a sheet still being counted cannot be filed as a correction that was
 * made.
 * <p>
 * The signature is the counter's: whoever stood at the shelf answers for what the sheet says.
 */
public final class StockCountSheetLayout {

    /** # | code | item | unit | book | counted | difference - sized for an upright A4. */
    static final float[] WIDTHS = {0.45f, 1.4f, 3.0f, 0.9f, 1.0f, 1.0f, 1.0f};

    private StockCountSheetLayout() {
    }

    /**
     * @param printedAt when it was printed, as the footer writes it, or blank for no footer
     */
    public static DocumentPdfPage of(StockCountDocument document, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        StockCountSummary header = document.header();
        InvoicePrintDocument.Letterhead company = letterhead == null ? InvoicePrintDocument.Letterhead.EMPTY : letterhead;

        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(labels.text("invoice.stock"), value(header.stockName())));
        details.add(DocumentPdfPage.Field.of(labels.text("item.stockcount.sheet.status"),
                labels.text(header.status().labelKey())));
        if (header.postedAt() != null) {
            details.add(DocumentPdfPage.Field.of(labels.text("item.stockcount.sheet.posted.at"),
                    Columns.DATE_TIME.format(header.postedAt())));
        }
        if (header.enteredBy() != null && !header.enteredBy().isBlank()) {
            details.add(DocumentPdfPage.Field.of(labels.text("stocks.transfer.slip.entered.by"), header.enteredBy()));
        }

        String[] headers = {"#", labels.text("stocks.transfer.column.code"), labels.text("item.stockcount.column.item"),
                labels.text("item.column.unit"), labels.text("item.stockcount.column.system"),
                labels.text("item.stockcount.column.counted"), labels.text("item.stockcount.column.difference")};
        List<String[]> rows = new ArrayList<>();
        int number = 1;
        for (StockCountLine line : document.lines()) {
            rows.add(new String[]{String.valueOf(number++), value(line.getBarcode()), value(line.getItemName()),
                    value(line.getUnitName()), quantity(line.getSystemQuantity()),
                    quantity(line.getCountedQuantity()), quantity(line.difference())});
        }

        return new DocumentPdfPage(
                labels.text("item.stockcount.sheet.title"),
                company.name(),
                letterheadLines(company, labels),
                company.logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("item.stockcount.sheet.number"), String.valueOf(header.id())),
                        DocumentPdfPage.Field.of(labels.text("item.stockcount.label.count.date"),
                                header.countDate() == null ? "" : header.countDate().toString())),
                details,
                headers,
                WIDTHS.clone(),
                rows,
                null,
                List.of(DocumentPdfPage.Field.of(labels.text("item.stockcount.kpi.line.count"),
                                String.valueOf(document.lines().size())),
                        DocumentPdfPage.Field.emphasised(labels.text("item.stockcount.kpi.diff.count"),
                                String.valueOf(document.lines().stream().filter(StockCountLine::hasDifference).count()))),
                labels.text("invoice.notes"),
                value(header.notes()),
                labels.text("item.stockcount.sheet.signature"),
                printedAt == null || printedAt.isBlank() ? ""
                        : labels.text("invoice.pdf.printed.at") + ": " + printedAt);
    }

    private static String quantity(double value) {
        return Columns.quantity(BigDecimal.valueOf(value));
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
