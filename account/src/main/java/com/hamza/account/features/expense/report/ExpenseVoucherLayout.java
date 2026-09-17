package com.hamza.account.features.expense.report;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.expense.ExpenseRow;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.controlsfx.table.Columns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A payment voucher - إذن صرف - for one expense, on an upright page (docs/expenses-plan.md §4, item 6).
 * <p>
 * <b>It is a document, not a report</b>, so it goes through {@link DocumentPdfPage} the way an invoice
 * does: the company's letterhead, what the paper is and its number, who was paid out of which till, one
 * line for what the money was for, the amount on a shaded band, and a line to sign. A report path would
 * have turned it into a table with a title and nowhere to sign.
 * <p>
 * <b>Everything on it is the stored row</b> - {@code ExpenseService.forVoucher} reads it again rather than
 * printing the list's copy, so a voucher printed after an edit on another machine says what the edit left.
 */
public final class ExpenseVoucherLayout {

    private ExpenseVoucherLayout() {
    }

    /**
     * @param printedAt when it was printed, as the footer writes it, or blank for no footer
     */
    public static DocumentPdfPage of(ExpenseRow expense, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        Objects.requireNonNull(expense, "expense");
        InvoicePrintDocument.Letterhead company = letterhead == null ? InvoicePrintDocument.Letterhead.EMPTY : letterhead;

        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(labels.text("expense.voucher.treasury"), value(expense.treasuryName())));
        addIfPresent(details, labels.text("expense.voucher.payee"), expense.payee());
        addIfPresent(details, labels.text("expense.voucher.employee"), expense.employeeName());
        addIfPresent(details, labels.text("expense.voucher.reference"), expense.referenceNo());
        details.add(DocumentPdfPage.Field.of(labels.text("expense.voucher.entered.by"), value(expense.userName())));

        String[] headers = {labels.text("expense.voucher.column.heading"), labels.text("expense.voucher.column.amount")};
        List<String[]> rows = List.<String[]>of(new String[]{value(expense.headingPath()), Columns.money(expense.amount())});

        return new DocumentPdfPage(
                labels.text("expense.voucher.title"),
                company.name(),
                letterheadLines(company, labels),
                company.logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("expense.voucher.number"), String.valueOf(expense.id())),
                        DocumentPdfPage.Field.of(labels.text("expense.voucher.date"),
                                expense.date() == null ? "" : expense.date().toString())),
                details,
                headers,
                new float[]{3f, 1.2f},
                rows,
                null,
                List.of(DocumentPdfPage.Field.emphasised(labels.text("expense.voucher.amount"),
                        Columns.money(expense.amount()))),
                labels.text("expense.voucher.notes"),
                value(expense.notes()),
                labels.text("expense.voucher.signature"),
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

    private static void addIfPresent(List<DocumentPdfPage.Field> fields, String label, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(DocumentPdfPage.Field.of(label, value));
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
