package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;

import java.util.List;
import java.util.Objects;

/**
 * Immutable print snapshot captured before work leaves the JavaFX thread.
 *
 * @param printedAt when the document was entered, as the receipt prints it
 * @param document  what either format prints - the receipt and the A4 page read the same one
 */
public record InvoicePrintRequest(
        List<ModelPrintInvoice> lines,
        String printedAt,
        boolean receipt,
        InvoicePrintDocument document) {

    public InvoicePrintRequest {
        lines = List.copyOf(lines);
        printedAt = printedAt == null ? "" : printedAt;
        Objects.requireNonNull(document, "document");
    }
}
