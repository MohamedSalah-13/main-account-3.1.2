package com.hamza.account.features.treasury;

import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.features.invoice.InvoicePdfLayout;
import com.hamza.account.features.invoice.InvoicePrintDocument;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The paper that goes with money changing hands at a treasury: a receipt voucher (سند قبض) for a
 * deposit, a payment voucher (سند صرف) for a withdrawal, and a transfer slip (إيصال تحويل) for a
 * move between two treasuries.
 * <p>
 * <b>Documents, not reports</b> - built on {@code ExpenseVoucherLayout} line for line: the
 * letterhead, what the paper is and its number, which treasury, one line for what the money was,
 * the amount on a shaded band, and a line to sign. A deposit of the owner's capital was recorded
 * with nothing anybody could sign or file; the transfers and deposits screens had no paper at all.
 * <p>
 * <b>Everything on it is the stored row.</b> The services read the movement again by its id
 * ({@code forVoucher}) rather than printing the list's copy, so a voucher says what the database
 * says on the machine that prints it.
 * <p>
 * <b>A transfer's fee is on the slip, and so is what left the source.</b> The destination receives
 * the amount in full and the source gives up the amount and the fee; a slip showing the amount
 * alone would not agree with the source treasury's own statement.
 * <p>
 * There is no amount in words. Arabic number-to-words is a piece of work of its own, and a wrong
 * one on a signed paper is worse than none.
 */
public final class TreasuryVoucherLayout {

    /** A deposit or a withdrawal as stored, with who entered it. */
    public record CashVoucher(CashMovement movement, String enteredBy) {
        public CashVoucher {
            Objects.requireNonNull(movement, "movement");
        }
    }

    /** A transfer as stored - its fee included - with who entered it. */
    public record TransferVoucher(TreasuryTransfer transfer, String enteredBy) {
        public TransferVoucher {
            Objects.requireNonNull(transfer, "transfer");
        }
    }

    private TreasuryVoucherLayout() {
    }

    /**
     * @param printedAt when it was printed, as the footer writes it, or blank for no footer
     */
    public static DocumentPdfPage of(CashVoucher voucher, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        CashMovement movement = voucher.movement();
        boolean receipt = movement.direction() == CashDirection.DEPOSIT;

        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(labels.text("treasury.voucher.treasury"), value(movement.treasuryName())));
        // Capital paid in and the owner's drawings are not ordinary cash, and the paper says which -
        // the point of the category is that the two are never mistaken for income or an expense.
        details.add(DocumentPdfPage.Field.of(labels.text("treasury.voucher.category"),
                labels.text(movement.category().labelKey())));
        addIfPresent(details, labels.text("treasury.voucher.entered.by"), voucher.enteredBy());

        String[] headers = {labels.text("treasury.voucher.column.statement"), labels.text("treasury.voucher.column.amount")};
        List<String[]> rows = List.<String[]>of(new String[]{value(movement.statement()), Columns.money(movement.amount())});

        return new DocumentPdfPage(
                labels.text(receipt ? "treasury.voucher.receipt.title" : "treasury.voucher.payment.title"),
                company(letterhead).name(),
                letterheadLines(company(letterhead), labels),
                company(letterhead).logo(),
                identity(labels, movement.id(), movement.date() == null ? "" : movement.date().toString()),
                details,
                headers,
                new float[]{3f, 1.2f},
                rows,
                null,
                List.of(DocumentPdfPage.Field.emphasised(labels.text("treasury.voucher.amount"),
                        Columns.money(movement.amount()))),
                labels.text("treasury.voucher.notes"),
                value(movement.description()),
                labels.text(receipt ? "treasury.voucher.signature.receiver" : "treasury.voucher.signature.payee"),
                footer(labels, printedAt));
    }

    public static DocumentPdfPage of(TransferVoucher voucher, InvoicePrintDocument.Letterhead letterhead,
                                     InvoicePdfLayout.Labels labels, String printedAt) {
        TreasuryTransfer transfer = voucher.transfer();
        boolean charged = transfer.fee().signum() > 0;

        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(labels.text("treasury.voucher.from"), value(transfer.fromTreasuryName())));
        details.add(DocumentPdfPage.Field.of(labels.text("treasury.voucher.to"), value(transfer.toTreasuryName())));
        addIfPresent(details, labels.text("treasury.voucher.entered.by"), voucher.enteredBy());

        String[] headers = {labels.text("treasury.voucher.column.statement"), labels.text("treasury.voucher.column.amount")};
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{labels.text("treasury.voucher.line.transferred"), Columns.money(transfer.amount())});
        if (charged) {
            rows.add(new String[]{labels.text("treasury.voucher.line.fee"), Columns.money(transfer.fee())});
        }

        List<DocumentPdfPage.Field> summary = new ArrayList<>();
        summary.add(DocumentPdfPage.Field.of(labels.text("treasury.voucher.received"), Columns.money(transfer.amount())));
        BigDecimal left = transfer.amount().add(transfer.fee());
        summary.add(DocumentPdfPage.Field.emphasised(labels.text("treasury.voucher.left.source"), Columns.money(left)));

        return new DocumentPdfPage(
                labels.text("treasury.voucher.transfer.title"),
                company(letterhead).name(),
                letterheadLines(company(letterhead), labels),
                company(letterhead).logo(),
                identity(labels, transfer.id(), transfer.transferDate() == null ? "" : transfer.transferDate().toString()),
                details,
                headers,
                new float[]{3f, 1.2f},
                rows,
                null,
                summary,
                labels.text("treasury.voucher.notes"),
                value(transfer.notes()),
                labels.text("treasury.voucher.signature.receiver"),
                footer(labels, printedAt));
    }

    private static List<DocumentPdfPage.Field> identity(InvoicePdfLayout.Labels labels, int number, String date) {
        return List.of(DocumentPdfPage.Field.of(labels.text("treasury.voucher.number"), String.valueOf(number)),
                DocumentPdfPage.Field.of(labels.text("treasury.voucher.date"), date));
    }

    private static InvoicePrintDocument.Letterhead company(InvoicePrintDocument.Letterhead letterhead) {
        return letterhead == null ? InvoicePrintDocument.Letterhead.EMPTY : letterhead;
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

    private static String footer(InvoicePdfLayout.Labels labels, String printedAt) {
        return printedAt == null || printedAt.isBlank() ? ""
                : labels.text("invoice.pdf.printed.at") + ": " + printedAt;
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
