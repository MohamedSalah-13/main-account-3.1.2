package com.hamza.account.features.stocktransfer;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The paper that travels with goods between two warehouses - إيصال تحويل مخزني.
 * <p>
 * A transfer had no paper at all: whoever loaded the van left with nothing to be signed at the other
 * end, and the only printout was a log of a date range. Built on {@code TreasuryVoucherLayout}'s
 * transfer slip line for line - the letterhead, what the paper is and its number, from and to, the
 * lines, a line to sign - so it goes through {@link DocumentPdfPage} and prints upright like every
 * other document, never sideways like a report.
 * <p>
 * <b>The quantities are the ones entered, in their own units</b>, and nothing on the paper adds them
 * up: the receiving storekeeper counts cartons and pieces against the lines, and a total of both would
 * be a number neither of them could check. What the summary gives instead is how many lines there are,
 * which is what a person checks first - a slip of seven lines on a van carrying six kinds of item.
 * <p>
 * The one signature is the receiver's, the same as the treasury's transfer slip: the paper exists to
 * be signed at the end the goods arrive at.
 */
public final class StockTransferSlipLayout {

    /** # | code | item | unit | quantity - sized for an upright A4. */
    static final float[] WIDTHS = {0.5f, 1.6f, 3.6f, 1.1f, 1.1f};

    private StockTransferSlipLayout() {
    }

    /**
     * @param printedAt when it was printed, as the footer writes it, or blank for no footer
     */
    public static DocumentPdfPage of(StockTransferSlip slip, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        StockTransferSummary header = slip.header();
        InvoicePrintDocument.Letterhead company = letterhead == null ? InvoicePrintDocument.Letterhead.EMPTY : letterhead;

        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(labels.text("stocks.transfer.from"), value(header.fromStockName())));
        details.add(DocumentPdfPage.Field.of(labels.text("stocks.transfer.to"), value(header.toStockName())));
        if (header.enteredBy() != null && !header.enteredBy().isBlank()) {
            details.add(DocumentPdfPage.Field.of(labels.text("stocks.transfer.slip.entered.by"), header.enteredBy()));
        }

        String[] headers = {"#", labels.text("stocks.transfer.column.code"), labels.text("stocks.transfer.item"),
                labels.text("item.column.unit"), labels.text("quantity")};
        List<String[]> rows = new ArrayList<>();
        int number = 1;
        for (StockTransferLineRow line : slip.lines()) {
            rows.add(new String[]{String.valueOf(number++), value(line.code()), value(line.itemName()),
                    value(line.unitName()), Columns.quantity(BigDecimal.valueOf(line.quantity()))});
        }

        return new DocumentPdfPage(
                labels.text("stocks.transfer.slip.title"),
                company.name(),
                letterheadLines(company, labels),
                company.logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("stocks.transfer.slip.number"), String.valueOf(header.id())),
                        DocumentPdfPage.Field.of(labels.text("stocks.transfer.date"),
                                header.transferDate() == null ? "" : header.transferDate().toString())),
                details,
                headers,
                WIDTHS.clone(),
                rows,
                null,
                List.of(DocumentPdfPage.Field.emphasised(labels.text("stocks.transfer.slip.lines"),
                        String.valueOf(slip.lines().size()))),
                labels.text("stocks.transfer.notes"),
                value(header.notes()),
                labels.text("stocks.transfer.slip.signature"),
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
