package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/** Prepares immutable invoice print data and executes the selected print format. */
public final class InvoicePrintService {

    /** Builds the upright page's content from the captured lines; asked only when one is printed. */
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

    public <T extends BasePurchasesAndSales> InvoicePrintRequest prepare(
            List<T> source,
            String partyName,
            int invoiceNumber,
            double discount,
            String printedAt,
            LocalDate invoiceDate,
            boolean receipt,
            DocumentSource document) throws DaoException {
        List<ModelPrintInvoice> lines = source.stream()
                .map(line -> new ModelPrintInvoice(
                        line.getItems().getNameItem(), line.getItems().getBarcode(),
                        line.getUnitsType().getUnit_name(), line.getPrice(), line.getQuantity(),
                        line.getTotal(), line.getDiscount(),
                        MoneyMath.asDouble(MoneyMath.subtract(
                                MoneyMath.decimal(line.getTotal()),
                                MoneyMath.decimal(line.getDiscount())))))
                .toList();
        return new InvoicePrintRequest(lines, partyName, invoiceNumber, discount,
                printedAt, invoiceDate, receipt, receipt ? null : document.build(lines));
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
            reports.printReceiptInvoice(request.lines(), request.partyName(),
                    request.invoiceNumber(), request.discount(), request.printedAt(),
                    request.invoiceDate().toString(), 0);
            return;
        }
        reports.printInvoice(request.document());
    }
}
