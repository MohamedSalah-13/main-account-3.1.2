package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.function.Supplier;

/** Prepares immutable invoice print data and executes the selected print format. */
public final class InvoicePrintService {

    /** Builds what the paper says from the captured lines: the saved header, the letterhead, the balance. */
    @FunctionalInterface
    public interface DocumentSource {
        InvoicePrintDocument build(List<ModelPrintInvoice> lines) throws DaoException;
    }

    private final Supplier<Print_Reports> reportsFactory;

    public InvoicePrintService() {
        this(Print_Reports::new);
    }

    InvoicePrintService(Supplier<Print_Reports> reportsFactory) {
        this.reportsFactory = reportsFactory;
    }

    /**
     * Captures the lines and builds the document for either format. The receipt reads the same
     * {@link InvoicePrintDocument} as the A4 page, so the two cannot quote different figures.
     *
     * @param printedAt when the document was entered, as the receipt prints it
     */
    public <T extends BasePurchasesAndSales> InvoicePrintRequest prepare(
            List<T> source,
            String printedAt,
            boolean receipt,
            DocumentSource document) throws DaoException {
        List<ModelPrintInvoice> lines = source.stream().map(InvoicePrintService::asShown).toList();
        return new InvoicePrintRequest(lines, printedAt, receipt, document.build(lines));
    }

    /**
     * The same, for lines read back from the database rather than off an invoice screen: a line of a
     * document typed in its party's currency prints what was typed on it, not the base it was stored at -
     * the header prints what was typed too (V83, docs/currency-plan.md §15 ق-د٩). A line with nothing typed
     * on it prints as it is.
     */
    public <T extends BasePurchasesAndSales> InvoicePrintRequest prepareStored(
            List<T> source,
            String printedAt,
            boolean receipt,
            DocumentSource document) throws DaoException {
        List<ModelPrintInvoice> lines = source.stream().map(InvoicePrintService::asTyped).toList();
        return new InvoicePrintRequest(lines, printedAt, receipt, document.build(lines));
    }

    private static ModelPrintInvoice asShown(BasePurchasesAndSales line) {
        return printed(line, line.getPrice(), line.getTotal(), line.getDiscount());
    }

    static ModelPrintInvoice asTyped(BasePurchasesAndSales line) {
        if (line.getPriceForeign() == null) {
            return asShown(line);
        }
        double discount = line.getDiscountForeign() == null ? 0 : line.getDiscountForeign().doubleValue();
        return printed(line, line.getPriceForeign().doubleValue(),
                MoneyMath.asDouble(MoneyMath.multiply(line.getQuantity(), line.getPriceForeign().doubleValue())),
                discount);
    }

    private static ModelPrintInvoice printed(BasePurchasesAndSales line, double price, double total,
                                             double discount) {
        return new ModelPrintInvoice(
                line.getItems().getNameItem(), line.getItems().getBarcode(),
                line.getUnitsType().getUnit_name(), price, line.getQuantity(),
                total, discount,
                MoneyMath.asDouble(MoneyMath.subtract(MoneyMath.decimal(total), MoneyMath.decimal(discount))));
    }

    /**
     * The receipt goes straight to the thermal printer and may run on any thread. The upright page
     * asks where to put the file - a dialog - so <b>it must be called on the JavaFX thread</b>; the
     * file itself is written in the background by {@code TablePdfReport}. It used to be called from
     * the masker pane's worker thread, where opening that dialog throws.
     */
    public void print(InvoicePrintRequest request) {
        Print_Reports reports = reportsFactory.get();
        if (request.receipt()) {
            reports.printReceiptInvoice(request.document(), request.printedAt());
            return;
        }
        reports.printInvoice(request.document());
    }
}
