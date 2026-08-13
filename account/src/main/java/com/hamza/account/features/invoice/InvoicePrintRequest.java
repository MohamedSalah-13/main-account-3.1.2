package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.ModelPrintInvoice;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable print snapshot captured before work leaves the JavaFX thread. */
public record InvoicePrintRequest(
        List<ModelPrintInvoice> lines,
        String partyName,
        int invoiceNumber,
        double discount,
        String printedAt,
        LocalDate invoiceDate,
        boolean receipt,
        Map<String, Object> invoiceDetails,
        String reportName) {

    public InvoicePrintRequest {
        lines = List.copyOf(lines);
        partyName = partyName == null ? "" : partyName;
        invoiceDetails = Collections.unmodifiableMap(new HashMap<>(invoiceDetails));
        reportName = reportName == null ? "" : reportName;
    }
}
