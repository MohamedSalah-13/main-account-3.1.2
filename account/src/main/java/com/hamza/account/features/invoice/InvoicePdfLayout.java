package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.export.DocumentPdfPage;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One invoice or return as the page a customer is handed.
 * <p>
 * What it prints, and why each is there:
 * <ul>
 *   <li><b>The letterhead</b> - the company's name, address, telephone, commercial and tax
 *       numbers and its logo. The Jasper template carried them; the first PDF did not.</li>
 *   <li><b>The document</b> - its name, number and date; who it is for and whether it is cash or
 *       deferred; the warehouse; the delegate on the sales side; on a return, the invoice it
 *       reverses and why.</li>
 *   <li><b>The lines</b>, numbered, with the barcode. Their totals line adds the discount and the
 *       net and counts the lines - it does not add the quantities, since a carton and a piece are
 *       not two of anything ({@code MultiInvoicePdfLayout} draws the same line).</li>
 *   <li><b>What it comes to</b> - the total, the additional discount when there is one, the net,
 *       what was paid (refunded, on a return) and what is left; and on a deferred document the
 *       party's balance before and after it.</li>
 * </ul>
 */
public final class InvoicePdfLayout {

    /** Resolves a message key. Named {@code text} so the message-key architecture test sees the keys. */
    @FunctionalInterface
    public interface Labels {
        String text(String key);
    }

    /** # | item | barcode | unit | quantity | price | discount | total - sized for an upright A4. */
    static final float[] WIDTHS = {0.5f, 3.2f, 1.7f, 1.0f, 1.0f, 1.2f, 1.0f, 1.4f};

    private InvoicePdfLayout() {
    }

    public static DocumentPdfPage of(InvoicePrintDocument document, Labels labels) {
        DocumentType type = document.type();
        String[] headers = {
                labels.text("invoice.pdf.column.row"),
                labels.text("invoice.pdf.column.item"),
                labels.text("invoice.pdf.column.barcode"),
                labels.text("invoice.pdf.column.unit"),
                labels.text("invoice.pdf.column.quantity"),
                labels.text("invoice.pdf.column.price"),
                labels.text("invoice.pdf.column.discount"),
                labels.text("invoice.pdf.column.total")};

        List<String[]> rows = new ArrayList<>();
        BigDecimal discountAll = MoneyMath.ZERO;
        BigDecimal netAll = MoneyMath.ZERO;
        int number = 1;
        for (ModelPrintInvoice line : document.lines()) {
            BigDecimal discount = MoneyMath.money(line.getDiscount());
            BigDecimal net = MoneyMath.money(line.getTotal_amount());
            discountAll = MoneyMath.add(discountAll, discount);
            netAll = MoneyMath.add(netAll, net);
            rows.add(new String[]{
                    String.valueOf(number++),
                    value(line.getName_item()),
                    value(line.getBarcode()),
                    value(line.getType()),
                    Columns.quantity(BigDecimal.valueOf(line.getQuantity())),
                    Columns.money(MoneyMath.money(line.getPrice())),
                    Columns.money(discount),
                    Columns.money(net)});
        }
        String[] totals = {"",
                labels.text("total") + "  -  " + labels.text("item.stockcount.kpi.line.count") + ": " + rows.size(),
                "", "", "", "", Columns.money(discountAll), Columns.money(netAll)};

        // "فاتورة بيع" / "مرتجع شراء": the period lock's name for the document, which is the name
        // of one document. invoiceText() names the family ("مبيعات") and read as a list heading.
        return new DocumentPdfPage(
                labels.text(type.periodLock().labelKey()),
                document.letterhead().name(),
                letterheadLines(document.letterhead(), labels),
                document.letterhead().logo(),
                List.of(DocumentPdfPage.Field.of(labels.text("invoice.pdf.number"), String.valueOf(document.number())),
                        DocumentPdfPage.Field.of(labels.text("invoice.pdf.date"), value(document.date()))),
                details(document, labels),
                headers, WIDTHS.clone(), rows, totals,
                summary(document, labels),
                labels.text("invoice.pdf.notes"),
                document.notes(),
                labels.text("invoice.pdf.signature"),
                document.printedAt().isEmpty() ? ""
                        : labels.text("invoice.pdf.printed.at") + ": " + document.printedAt());
    }

    private static List<String> letterheadLines(InvoicePrintDocument.Letterhead letterhead, Labels labels) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, "", letterhead.address());
        addIfPresent(lines, labels.text("invoice.pdf.phone") + ": ", letterhead.phone());
        addIfPresent(lines, labels.text("invoice.pdf.commercial") + ": ", letterhead.commercial());
        addIfPresent(lines, labels.text("invoice.pdf.tax") + ": ", letterhead.tax());
        return lines;
    }

    private static void addIfPresent(List<String> lines, String prefix, String value) {
        if (!value.isBlank()) {
            lines.add(prefix + value);
        }
    }

    private static List<DocumentPdfPage.Field> details(InvoicePrintDocument document, Labels labels) {
        DocumentType type = document.type();
        List<DocumentPdfPage.Field> details = new ArrayList<>();
        details.add(DocumentPdfPage.Field.of(type.partyKind() == PartyKind.CUSTOMER
                        ? labels.text("invoice.pdf.party.customer") : labels.text("invoice.pdf.party.supplier"),
                value(document.partyName())));
        details.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.payment.type"),
                document.deferred() ? labels.text("defer") : labels.text("cash")));
        if (!document.stockName().isBlank()) {
            details.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.stock"), document.stockName()));
        }
        if (!document.delegateName().isBlank()) {
            details.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.delegate"), document.delegateName()));
        }
        if (document.sourceInvoiceNumber() > 0) {
            details.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.source.invoice"),
                    String.valueOf(document.sourceInvoiceNumber())));
        }
        if (!document.returnReason().isBlank()) {
            details.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.return.reason"), document.returnReason()));
        }
        return details;
    }

    private static List<DocumentPdfPage.Field> summary(InvoicePrintDocument document, Labels labels) {
        List<DocumentPdfPage.Field> summary = new ArrayList<>();
        summary.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.summary.total"),
                Columns.money(document.total())));
        if (document.discount().signum() != 0) {
            summary.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.summary.discount"),
                    Columns.money(document.discount())));
        }
        summary.add(DocumentPdfPage.Field.emphasised(labels.text("invoice.pdf.summary.net"),
                Columns.money(document.net())));
        summary.add(DocumentPdfPage.Field.of(document.type().isReturn()
                        ? labels.text("invoice.pdf.summary.refunded") : labels.text("invoice.pdf.summary.paid"),
                Columns.money(document.paid())));
        summary.add(new DocumentPdfPage.Field(labels.text("invoice.pdf.summary.rest"),
                Columns.money(document.rest()), document.rest().signum() != 0));
        if (document.balance() != null) {
            summary.add(DocumentPdfPage.Field.of(labels.text("invoice.pdf.balance.before"),
                    Columns.money(document.balance().before())));
            summary.add(DocumentPdfPage.Field.emphasised(labels.text("invoice.pdf.balance.after"),
                    Columns.money(document.balance().after())));
        }
        return summary;
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
