package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Immutable print snapshot captured before work leaves the JavaFX thread.
 *
 * @param document what the upright page prints; null for a receipt, which the thermal template
 *                 builds from the other fields
 */
public record InvoicePrintRequest(
        List<ModelPrintInvoice> lines,
        String partyName,
        int invoiceNumber,
        double discount,
        String printedAt,
        LocalDate invoiceDate,
        boolean receipt,
        InvoicePrintDocument document) {

    public InvoicePrintRequest {
        lines = List.copyOf(lines);
        partyName = partyName == null ? "" : partyName;
        if (!receipt) {
            Objects.requireNonNull(document, "document");
        }
    }
}
