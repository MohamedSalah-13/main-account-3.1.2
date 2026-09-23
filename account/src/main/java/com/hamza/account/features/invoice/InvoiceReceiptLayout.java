package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The 80mm receipt's lines and summary, written out as the text the thermal template prints.
 * <p>
 * <b>The figures come from the same {@link InvoicePrintDocument} as the A4 page</b> - the saved header
 * and {@code DocumentLedgerEffect} - so the receipt and the invoice cannot quote a customer two
 * different amounts. The template used to receive the lines as doubles and the totals as two loose
 * parameters, printed them unformatted ({@code 10.5}, {@code 223.0}), and had no place at all for what
 * was paid, what was left or the party's balance: a deferred sale's receipt said what the goods came
 * to and nothing about what the customer now owed.
 * <p>
 * <b>Everything arrives as text.</b> A {@code pattern} on a Jasper field is resolved against the
 * report's locale, which prints Arabic-Indic digits beside Latin ones (see {@code CLAUDE.md}, "Printed
 * reports"); writing the figures here with {@link Columns#money} keeps one definition of how an amount
 * looks, shared with every screen.
 * <p>
 * The summary is a list of rows rather than fixed fields so that a row which does not apply - no
 * additional discount, no balance on a cash sale - is simply absent, instead of leaving a gap.
 *
 * @param linesTotalLabel what the lines' totals line is called, with the number of lines in it
 * @param linesTotal      the lines after their own discounts, added up
 */
public record InvoiceReceiptLayout(List<Line> lines, String linesTotalLabel, String linesTotal, List<Row> summary) {

    public InvoiceReceiptLayout {
        lines = List.copyOf(lines);
        summary = List.copyOf(summary);
    }

    public static InvoiceReceiptLayout of(InvoicePrintDocument document, InvoicePdfLayout.Labels labels) {
        List<Line> lines = new ArrayList<>();
        BigDecimal net = MoneyMath.ZERO;
        for (ModelPrintInvoice line : document.lines()) {
            BigDecimal lineNet = MoneyMath.money(line.getTotal_amount());
            net = MoneyMath.add(net, lineNet);
            lines.add(new Line(value(line.getName_item()),
                    Columns.money(MoneyMath.money(line.getPrice())),
                    Columns.quantity(BigDecimal.valueOf(line.getQuantity())),
                    Columns.money(MoneyMath.money(line.getDiscount())),
                    Columns.money(lineNet)));
        }
        String linesLabel = labels.text("total") + " - "
                + labels.text("item.stockcount.kpi.line.count") + ": " + lines.size();

        List<Row> summary = new ArrayList<>();
        summary.add(new Row(labels.text("invoice.pdf.payment.type"),
                document.deferred() ? labels.text("defer") : labels.text("cash"), false));
        if (document.currency() != null) {
            summary.add(new Row(labels.text("invoice.pdf.currency"), document.currency().figuresIn().code(), false));
        }
        summary.add(new Row(labels.text("invoice.pdf.summary.total"), Columns.money(document.total()), false));
        if (document.discount().signum() != 0) {
            summary.add(new Row(labels.text("invoice.pdf.summary.discount"),
                    Columns.money(document.discount()), false));
        }
        summary.add(new Row(labels.text("invoice.pdf.summary.net"), Columns.money(document.net()), true));
        summary.add(new Row(document.type().isReturn()
                ? labels.text("invoice.pdf.summary.refunded") : labels.text("invoice.pdf.summary.paid"),
                Columns.money(document.paid()), false));
        summary.add(new Row(labels.text("invoice.pdf.summary.rest"), Columns.money(document.rest()),
                document.rest().signum() != 0));
        if (document.balance() != null) {
            summary.add(new Row(labels.text("invoice.pdf.balance.before"),
                    Columns.money(document.balance().before()), false));
            summary.add(new Row(labels.text("invoice.pdf.balance.after"),
                    Columns.money(document.balance().after()), true));
        }
        for (String[] row : InvoicePdfLayout.currencyRows(document.currency(), labels)) {
            summary.add(new Row(row[0], row[1], false));
        }
        return new InvoiceReceiptLayout(lines, linesLabel, Columns.money(net), summary);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * One printed line. A class with getters, not a record: the template reads it through
     * {@code JRBeanCollectionDataSource}, which looks for {@code getName_item()}, not {@code name_item()}.
     */
    public static final class Line {
        private final String nameItem;
        private final String price;
        private final String quantity;
        private final String discount;
        private final String totalAmount;

        public Line(String nameItem, String price, String quantity, String discount, String totalAmount) {
            this.nameItem = nameItem;
            this.price = price;
            this.quantity = quantity;
            this.discount = discount;
            this.totalAmount = totalAmount;
        }

        public String getName_item() {
            return nameItem;
        }

        public String getPrice() {
            return price;
        }

        public String getQuantity() {
            return quantity;
        }

        public String getDiscount() {
            return discount;
        }

        public String getTotal_amount() {
            return totalAmount;
        }
    }

    /** One summary row; {@code bold} marks the figure the reader is looking for. */
    public static final class Row {
        private final String label;
        private final String value;
        private final Boolean bold;

        public Row(String label, String value, boolean bold) {
            this.label = label;
            this.value = value;
            this.bold = bold;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public Boolean getBold() {
            return bold;
        }
    }
}
