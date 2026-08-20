package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;

import java.util.List;

/** Values produced by a successful atomic invoice header/line save. */
public record InvoiceSaveResult(
        int invoiceNumber,
        boolean updated,
        BaseTotals invoice,
        InvoicePaymentTerms payment,
        List<? extends BasePurchasesAndSales> persistedLines) {

    public InvoiceSaveResult {
        persistedLines = List.copyOf(persistedLines);
    }
}
