package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.features.export.TreePdfLayout;
import com.hamza.account.finance.MoneyMath;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The detailed totals report as a tree: each selected invoice is a heading (its number, date and
 * party), its lines under it, and the invoice's own total; the grand total closes the report. It is
 * the shape the A4 Jasper template printed, which grouped the lines by invoice number.
 * <p>
 * <b>A line's {@code total} is before its discount.</b> {@code InvoiceLineService.recalculate}
 * stores {@code quantity * price} there and keeps the net in {@code total_after_discount}, which
 * {@link PrintPurchaseWithName} does not carry. The net is therefore worked out here, as the
 * template did - printing {@code total} under a "net" heading overstates every discounted line.
 * <p>
 * Quantities are not summed: two lines of one invoice can be in different units, and a carton
 * plus a piece is not two of anything.
 */
public record MultiInvoicePdfLayout(TreePdfLayout tree, int invoiceCount, BigDecimal net) {

    /** Resolves a message key. Named {@code text} so the message-key architecture test sees the keys. */
    @FunctionalInterface
    public interface Labels {
        String text(String key);
    }

    public static MultiInvoicePdfLayout of(List<PrintPurchaseWithName> source, Labels labels) {
        String[] headers = {
                labels.text("column.item_name"), labels.text("item.column.unit"),
                labels.text("column.quantity"), labels.text("column.price"),
                labels.text("invoice.total.before.discount"), labels.text("column.discount"),
                labels.text("column.total_amount")};
        float[] widths = {3.6f, 1.2f, 1.1f, 1.3f, 1.5f, 1.2f, 1.5f};
        // Properties strips a value's leading blank, so the bundle's " — " arrives as "— ".
        String separator = "  " + labels.text("invoice.report.filter.separator").strip() + "  ";

        Map<Integer, List<PrintPurchaseWithName>> byInvoice = new LinkedHashMap<>();
        for (PrintPurchaseWithName line : source) {
            byInvoice.computeIfAbsent(line.getNum(), number -> new ArrayList<>()).add(line);
        }

        List<TreePdfLayout.Branch> branches = new ArrayList<>();
        BigDecimal grossAll = MoneyMath.ZERO;
        BigDecimal discountAll = MoneyMath.ZERO;
        BigDecimal netAll = MoneyMath.ZERO;
        for (Map.Entry<Integer, List<PrintPurchaseWithName>> invoice : byInvoice.entrySet()) {
            PrintPurchaseWithName first = invoice.getValue().getFirst();
            String title = labels.text("column.code_invoice") + ": " + invoice.getKey()
                    + separator + labels.text("column.date") + ": " + value(first.getDate())
                    + separator + labels.text("column.name") + ": " + value(first.getName());

            List<String[]> rows = new ArrayList<>();
            BigDecimal gross = MoneyMath.ZERO;
            BigDecimal discount = MoneyMath.ZERO;
            BigDecimal net = MoneyMath.ZERO;
            for (PrintPurchaseWithName line : invoice.getValue()) {
                BigDecimal lineGross = MoneyMath.money(line.getTotal());
                BigDecimal lineDiscount = MoneyMath.money(line.getDiscount());
                BigDecimal lineNet = MoneyMath.subtract(lineGross, lineDiscount);
                gross = MoneyMath.add(gross, lineGross);
                discount = MoneyMath.add(discount, lineDiscount);
                net = MoneyMath.add(net, lineNet);
                rows.add(new String[]{
                        value(line.getItemName()),
                        line.getUnitsType() == null ? "-" : value(line.getUnitsType().getUnit_name()),
                        Columns.quantity(BigDecimal.valueOf(line.getQuantity())),
                        Columns.money(MoneyMath.money(line.getPrice())),
                        Columns.money(lineGross),
                        Columns.money(lineDiscount),
                        Columns.money(lineNet)});
            }
            String label = labels.text("invoice.report.detailed.invoice.total") + separator
                    + labels.text("item.stockcount.kpi.line.count") + ": " + rows.size();
            branches.add(new TreePdfLayout.Branch(title, rows,
                    new String[]{label, "", "", "", Columns.money(gross), Columns.money(discount),
                            Columns.money(net)}));
            grossAll = MoneyMath.add(grossAll, gross);
            discountAll = MoneyMath.add(discountAll, discount);
            netAll = MoneyMath.add(netAll, net);
        }

        String[] totals = {
                labels.text("invoice.report.detailed.grand.total") + separator
                        + labels.text("invoice.report.column.invoices") + ": " + byInvoice.size(),
                "", "", "", Columns.money(grossAll), Columns.money(discountAll), Columns.money(netAll)};
        return new MultiInvoicePdfLayout(new TreePdfLayout(headers, widths, branches, totals),
                byInvoice.size(), netAll);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
